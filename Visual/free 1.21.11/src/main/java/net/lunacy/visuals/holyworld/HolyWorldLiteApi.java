package net.lunacy.visuals.holyworld;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.gui.ClickGuiScreen;
import net.lunacy.visuals.module.Module;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HolyWorld In-Game LiteAPI client integration (channel liteapi:feature-control).
 * Specification: https://wiki.holyworld.me/lite/api/protocol
 * Feature Control: https://wiki.holyworld.me/lite/api/feature
 */
public final class HolyWorldLiteApi {
    public static final String CHANNEL_NAMESPACE = "liteapi";
    public static final String CHANNEL_PATH = "feature-control";
    public static final String METHOD_CHECK_FEATURES = "checkFeatures";
    public static final long RATE_LIMIT_MS = 10_000L;
    public static final long RETRY_INTERVAL_MS = 10_500L; // Retry >= 10 сек (согласно rate limit)
    private static final int MAX_RETRIES = 3;

    private static final Gson GSON = new Gson();
    private static final Set<String> BLOCKED_FEATURES = ConcurrentHashMap.newKeySet();
    private static final AtomicLong BLOCKLIST_REVISION = new AtomicLong(0L);

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HolyWorld-LiteAPI-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean initialized = false;
    private static volatile boolean connected = false;
    private static volatile boolean pendingVerification = false;
    private static volatile String pendingRequestId = null;
    private static volatile long lastRequestTime = 0L;
    private static int retryCount = 0;
    private static ScheduledFuture<?> retryTask = null;

    private HolyWorldLiteApi() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        PayloadTypeRegistry.playC2S().register(LiteApiPayload.ID, LiteApiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LiteApiPayload.ID, LiteApiPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(LiteApiPayload.ID, (payload, context) -> {
            handleMessage(payload.json());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            onDisconnect();
        });
    }

    public static synchronized void onJoin() {
        connected = true;
        cancelRetry();
        lastRequestTime = 0L; // Критичный фикс reconnect: сброс таймера для мгновенной отправки
        pendingRequestId = null;
        retryCount = 0;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.isInSingleplayer()) {
            // В одиночной игре сервер отсутствует — ограничения LiteAPI не применяются
            pendingVerification = false;
            BLOCKED_FEATURES.clear();
            BLOCKLIST_REVISION.incrementAndGet();
            notifyGui();
            return;
        }

        // Fail-closed: на входе до ответа сервера все подконтрольные функции считаются заблокированными
        pendingVerification = true;
        applyFailClosedState();

        // Немедленная отправка запроса
        checkFeatures();
    }

    public static synchronized void onDisconnect() {
        connected = false;
        cancelRetry();
        pendingRequestId = null;
        lastRequestTime = 0L;
        retryCount = 0;
        pendingVerification = false;
        BLOCKED_FEATURES.clear();
        BLOCKLIST_REVISION.incrementAndGet();
        notifyGui();
    }

    public static boolean isFeatureBlocked(String feature) {
        if (feature == null) return false;
        // Fail-closed: пока верификация в процессе, все подконтрольные функции заблокированы
        if (pendingVerification) return true;
        if (BLOCKED_FEATURES.isEmpty()) return false;
        String normalized = feature.toLowerCase(Locale.ROOT).trim();
        return BLOCKED_FEATURES.contains(normalized);
    }

    public static Set<String> getBlockedFeatures() {
        return Collections.unmodifiableSet(BLOCKED_FEATURES);
    }

    public static long getBlocklistRevision() {
        return BLOCKLIST_REVISION.get();
    }

    public static synchronized boolean checkFeatures() {
        if (!connected) return false;

        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (lastRequestTime > 0L && elapsed < RATE_LIMIT_MS) {
            long remaining = Math.max(100L, RATE_LIMIT_MS - elapsed);
            scheduleRetry(remaining);
            return false;
        }
        lastRequestTime = now;

        String reqId = UUID.randomUUID().toString();
        pendingRequestId = reqId;

        List<String> featureList = collectFeatureNames();
        JsonObject root = new JsonObject();
        root.addProperty("id", reqId);
        root.addProperty("method", METHOD_CHECK_FEATURES);

        JsonObject payload = new JsonObject();
        payload.addProperty("client", LunacyVisuals.MOD_ID);

        JsonArray featuresArray = new JsonArray();
        for (String feature : featureList) {
            featuresArray.add(feature);
        }
        payload.add("features", featuresArray);
        root.add("payload", payload);

        String json = GSON.toJson(root);
        boolean sent = false;
        try {
            ClientPlayNetworking.send(new LiteApiPayload(json));
            sent = true;
        } catch (Exception e) {
            LunacyVisuals.LOGGER.warn("LiteAPI: Failed to send checkFeatures packet: {}", e.getMessage());
        }

        // Запуск timeout/retry на случай потери пакета или отсутствия ответа (>= 10 сек)
        scheduleRetry(RETRY_INTERVAL_MS);
        return sent;
    }

    public static void handleMessage(String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;
            JsonObject obj = parsed.getAsJsonObject();

            // 1. Push-событие от сервера (не имеет ok и id)
            if (obj.has("event") && !obj.has("ok")) {
                handlePushEvent(obj);
                return;
            }

            // 2. Ответ сервера на наш запрос checkFeatures
            if (obj.has("id")) {
                String respId = obj.get("id").getAsString();
                synchronized (HolyWorldLiteApi.class) {
                    if (pendingRequestId == null || !pendingRequestId.equals(respId)) {
                        LunacyVisuals.LOGGER.debug("LiteAPI: Ignored packet with mismatched or outdated id: {}", respId);
                        return;
                    }
                }

                boolean ok = obj.has("ok") && obj.get("ok").getAsBoolean();
                if (!ok) {
                    String error = obj.has("error") ? obj.get("error").getAsString() : "UNKNOWN";
                    LunacyVisuals.LOGGER.warn("LiteAPI: Server returned error: {}", error);
                    // При ошибке остаемся в fail-closed и планируем retry >= 10 сек
                    synchronized (HolyWorldLiteApi.class) {
                        scheduleRetry(RETRY_INTERVAL_MS);
                    }
                    return;
                }

                // ok == true: проверяем валидность payload и массива blocklist
                if (!obj.has("payload") || !obj.get("payload").isJsonObject()) {
                    LunacyVisuals.LOGGER.warn("LiteAPI: Malformed response: missing payload object (retaining fail-closed)");
                    synchronized (HolyWorldLiteApi.class) {
                        scheduleRetry(RETRY_INTERVAL_MS);
                    }
                    return;
                }

                JsonObject payloadObj = obj.getAsJsonObject("payload");
                if (!payloadObj.has("blocklist") || !payloadObj.get("blocklist").isJsonArray()) {
                    LunacyVisuals.LOGGER.warn("LiteAPI: Malformed response: missing blocklist array in payload (retaining fail-closed)");
                    synchronized (HolyWorldLiteApi.class) {
                        scheduleRetry(RETRY_INTERVAL_MS);
                    }
                    return;
                }

                // Успешный валидный ответ сервера
                synchronized (HolyWorldLiteApi.class) {
                    pendingVerification = false;
                    pendingRequestId = null;
                    retryCount = 0;
                    cancelRetry();
                }

                Set<String> serverBlocklist = new HashSet<>();
                for (JsonElement el : payloadObj.getAsJsonArray("blocklist")) {
                    if (el.isJsonPrimitive()) {
                        serverBlocklist.add(el.getAsString().toLowerCase(Locale.ROOT).trim());
                    }
                }
                updateBlocklist(serverBlocklist);
            }
        } catch (Exception e) {
            LunacyVisuals.LOGGER.warn("LiteAPI: Failed to parse packet: {}", e.getMessage());
        }
    }

    private static void handlePushEvent(JsonObject obj) {
        if (obj.has("payload") && obj.get("payload").isJsonObject()) {
            JsonObject p = obj.getAsJsonObject("payload");
            if (p.has("blocklist") && p.get("blocklist").isJsonArray()) {
                Set<String> updated = new HashSet<>();
                for (JsonElement el : p.getAsJsonArray("blocklist")) {
                    if (el.isJsonPrimitive()) {
                        updated.add(el.getAsString().toLowerCase(Locale.ROOT).trim());
                    }
                }
                synchronized (HolyWorldLiteApi.class) {
                    pendingVerification = false;
                    pendingRequestId = null;
                    retryCount = 0;
                    cancelRetry();
                }
                updateBlocklist(updated);
            }
        }
    }

    private static void applyFailClosedState() {
        Set<String> allControlled = new HashSet<>(collectFeatureNames());
        BLOCKED_FEATURES.clear();
        BLOCKED_FEATURES.addAll(allControlled);
        BLOCKLIST_REVISION.incrementAndGet();
        disableBlockedModules();
        notifyGui();
    }

    private static void updateBlocklist(Set<String> updated) {
        BLOCKED_FEATURES.clear();
        BLOCKED_FEATURES.addAll(updated);
        BLOCKLIST_REVISION.incrementAndGet();
        disableBlockedModules();
        notifyGui();
    }

    private static void disableBlockedModules() {
        try {
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime != null && runtime.modules() != null) {
                for (Module mod : runtime.modules().all()) {
                    if (isFeatureBlocked(mod.id()) && mod.isEnabled()) {
                        mod.setEnabled(false);
                        LunacyVisuals.LOGGER.info("HolyWorld LiteAPI: Module '{}' disabled by server feature-control", mod.id());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void notifyGui() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (client.currentScreen instanceof ClickGuiScreen clickGui) {
                        clickGui.invalidateModuleCache();
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void scheduleRetry(long delayMs) {
        cancelRetry();
        if (!connected) return;

        retryTask = SCHEDULER.schedule(() -> {
            synchronized (HolyWorldLiteApi.class) {
                if (connected && (pendingVerification || pendingRequestId != null)) {
                    if (retryCount >= MAX_RETRIES) {
                        LunacyVisuals.LOGGER.info("LiteAPI: Server does not support LiteAPI feature-control (no response after {} attempts). Allowing features.", MAX_RETRIES);
                        pendingVerification = false;
                        pendingRequestId = null;
                        BLOCKED_FEATURES.clear();
                        BLOCKLIST_REVISION.incrementAndGet();
                        notifyGui();
                        return;
                    }
                    retryCount++;
                    LunacyVisuals.LOGGER.info("LiteAPI: Retrying checkFeatures request (attempt {}/{})...", retryCount, MAX_RETRIES);
                    checkFeatures();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static synchronized void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    private static List<String> collectFeatureNames() {
        Set<String> features = new HashSet<>();
        features.add("xray");
        features.add("fly");
        features.add("speed");
        features.add("zoom");
        features.add("fullbright");

        try {
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime != null && runtime.modules() != null) {
                for (Module mod : runtime.modules().all()) {
                    features.add(mod.id().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {}

        return new ArrayList<>(features);
    }
}
