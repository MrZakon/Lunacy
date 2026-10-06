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
import net.lunacy.visuals.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HolyWorld In-Game LiteAPI client integration (channel liteapi:feature-control).
 * Specification: https://wiki.holyworld.me/lite/api/protocol
 */
public final class HolyWorldLiteApi {
    public static final String CHANNEL_NAMESPACE = "liteapi";
    public static final String CHANNEL_PATH = "feature-control";
    public static final String METHOD_CHECK_FEATURES = "checkFeatures";
    public static final long RATE_LIMIT_MS = 10_000L;

    private static final Gson GSON = new Gson();
    private static final Set<String> BLOCKED_FEATURES = ConcurrentHashMap.newKeySet();
    private static volatile long lastRequestTime = 0L;
    private static volatile boolean initialized = false;

    private HolyWorldLiteApi() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        PayloadTypeRegistry.serverboundPlay().register(LiteApiPayload.TYPE, LiteApiPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LiteApiPayload.TYPE, LiteApiPayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(LiteApiPayload.TYPE, (payload, context) -> {
            handleMessage(payload.json());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clearBlocklist();
            checkFeatures();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearBlocklist();
        });
    }

    public static boolean isFeatureBlocked(String feature) {
        if (feature == null || BLOCKED_FEATURES.isEmpty()) return false;
        String normalized = feature.toLowerCase(Locale.ROOT).trim();
        return BLOCKED_FEATURES.contains(normalized);
    }

    public static Set<String> getBlockedFeatures() {
        return Collections.unmodifiableSet(BLOCKED_FEATURES);
    }

    public static void clearBlocklist() {
        BLOCKED_FEATURES.clear();
    }

    public static boolean checkFeatures() {
        long now = System.currentTimeMillis();
        if (now - lastRequestTime < RATE_LIMIT_MS) {
            return false;
        }
        lastRequestTime = now;

        List<String> featureList = collectFeatureNames();
        JsonObject root = new JsonObject();
        root.addProperty("id", UUID.randomUUID().toString());
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
        try {
            ClientPlayNetworking.send(new LiteApiPayload(json));
            return true;
        } catch (Exception e) {
            LunacyVisuals.LOGGER.debug("Could not send LiteAPI checkFeatures packet: {}", e.getMessage());
            return false;
        }
    }

    public static void handleMessage(String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;
            JsonObject obj = parsed.getAsJsonObject();

            if (obj.has("event") && !obj.has("ok")) {
                handlePushEvent(obj);
                return;
            }

            if (obj.has("ok") && obj.get("ok").getAsBoolean()) {
                if (obj.has("payload") && obj.get("payload").isJsonObject()) {
                    JsonObject p = obj.getAsJsonObject("payload");
                    if (p.has("blocklist") && p.get("blocklist").isJsonArray()) {
                        Set<String> updated = new HashSet<>();
                        for (JsonElement el : p.getAsJsonArray("blocklist")) {
                            if (el.isJsonPrimitive()) {
                                updated.add(el.getAsString().toLowerCase(Locale.ROOT).trim());
                            }
                        }
                        updateBlocklist(updated);
                    }
                }
            }
        } catch (Exception e) {
            LunacyVisuals.LOGGER.warn("Failed to parse HolyWorld LiteAPI payload: {}", e.getMessage());
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
                updateBlocklist(updated);
            }
        }
    }

    private static void updateBlocklist(Set<String> updated) {
        BLOCKED_FEATURES.clear();
        BLOCKED_FEATURES.addAll(updated);

        if (!BLOCKED_FEATURES.isEmpty()) {
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
