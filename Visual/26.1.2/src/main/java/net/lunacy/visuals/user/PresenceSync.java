package net.lunacy.visuals.user;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Anonymous online heartbeat and roster sync for the client-side Lunacy badge. */
public final class PresenceSync {
    private static final URI API = URI.create("https://lunacyvisual.fun/api/presence/");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static long nextHeartbeat;
    private static long nextRoster;
    private static volatile String lastLeavePayload;
    private PresenceSync() {}

    public static void tick(Object client) {
        long now = System.currentTimeMillis();
        if (now < nextHeartbeat && now < nextRoster) return;
        Object player = read(client, "player");
        if (player == null) return;
        Object nameValue = call(player, "getName");
        String name = nameValue == null ? "" : String.valueOf(call(nameValue, "getString"));
        Object uuidValue = call(player, "getUUID");
        if (uuidValue == null) uuidValue = call(player, "getUuid");
        if (uuidValue == null) return;
        String server = serverAddress(client);
        if (server.isBlank()) return;
        String version = "26.1.2";
        if (now >= nextHeartbeat) {
            nextHeartbeat = now + 20_000;
            lastLeavePayload = "{\"uuid\":\"" + uuidValue + "\",\"server\":\"" + esc(server) + "\"}";
            request("heartbeat", "{\"uuid\":\"" + uuidValue + "\",\"name\":\"" + esc(name) + "\",\"server\":\"" + esc(server) + "\",\"version\":\"" + version + "\"}", false);
        }
        if (now >= nextRoster && RUNNING.compareAndSet(false, true)) {
            nextRoster = now + 10_000;
            request("online?server=" + java.net.URLEncoder.encode(server, java.nio.charset.StandardCharsets.UTF_8), null, true);
        }
    }

    /** Immediately removes this client from the online roster on graceful shutdown. */
    public static void leave() {
        String payload = lastLeavePayload;
        if (payload == null) return;
        try {
            HttpRequest request = HttpRequest.newBuilder(API.resolve("leave"))
                    .timeout(Duration.ofSeconds(1)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private static void request(String path, String body, boolean roster) {
        HttpRequest.Builder b = HttpRequest.newBuilder(API.resolve(path)).timeout(Duration.ofSeconds(5));
        if (body == null) b.GET(); else b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            if (roster && r.statusCode() == 200) {
                Matcher m = Pattern.compile("\\\"uuid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(r.body());
                while (m.find()) {
                    try { LunacyUserService.registerUser(m.group(2), UUID.fromString(m.group(1))); } catch (IllegalArgumentException ignored) {}
                }
            }
        }).exceptionally(e -> null).whenComplete((v, e) -> { if (roster) RUNNING.set(false); });
    }

    private static String serverAddress(Object client) {
        Object entry = call(client, "getCurrentServerEntry");
        Object address = entry == null ? null : read(entry, "address");
        return address == null ? "singleplayer" : String.valueOf(address);
    }
    private static Object read(Object o, String field) { try { Field f = o.getClass().getField(field); return f.get(o); } catch (Exception e) { try { Field f=o.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(o); } catch(Exception ignored){return null;} } }
    private static Object call(Object o, String name) { try { for (Method m:o.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==0) return m.invoke(o); } catch(Exception ignored){} return null; }
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}

