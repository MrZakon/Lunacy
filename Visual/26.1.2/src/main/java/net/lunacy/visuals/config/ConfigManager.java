package net.lunacy.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleManager;
import net.lunacy.visuals.module.setting.Setting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Версионированная конфигурация с атомарной заменой файла и резервированием
 * повреждённого JSON.
 */
public final class ConfigManager {
    public static final int SCHEMA_VERSION = 5;
    private static final long SAVE_DEBOUNCE_NANOS = 500_000_000L;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path configPath;
    private final ModuleManager modules;
    private final ClientSettings clientSettings;
    private final Map<String, HudPosition> hudPositions = new HashMap<>();
    private boolean dirty;
    private long dirtySinceNanos;
    private boolean loading;

    public ConfigManager(Path configPath, ModuleManager modules) {
        this(configPath, modules, null);
    }

    public ConfigManager(Path configPath, ModuleManager modules, ClientSettings clientSettings) {
        this.configPath = Objects.requireNonNull(configPath, "configPath").toAbsolutePath().normalize();
        this.modules = Objects.requireNonNull(modules, "modules");
        this.clientSettings = clientSettings;
    }

    public void bindChangeTracking() {
        modules.all().forEach(module -> {
            module.onStateChanged(ignored -> markDirty());
            module.settings().forEach(setting -> setting.onChanged(ignored -> markDirty()));
        });
        if (clientSettings != null) {
            clientSettings.all().forEach(setting -> setting.onChanged(ignored -> markDirty()));
        }
    }

    public void load() {
        loading = true;
        try {
            if (!Files.isRegularFile(configPath)) {
                bindChangeTracking();
                markDirty();
                flush();
                return;
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (!rootElement.isJsonObject()) {
                    throw new IOException("Config root is not a JSON object");
                }
                readRoot(rootElement.getAsJsonObject());
            } catch (Exception exception) {
                backupCorruptedFile();
                LunacyVisuals.LOGGER.error("Could not read {}; defaults will be used", configPath, exception);
            }
            bindChangeTracking();
        } finally {
            loading = false;
            dirty = false;
        }
    }

    public void tick() {
        if (dirty && System.nanoTime() - dirtySinceNanos >= SAVE_DEBOUNCE_NANOS) {
            flush();
        }
    }

    public void markDirty() {
        if (loading) {
            return;
        }
        if (!dirty) {
            dirtySinceNanos = System.nanoTime();
        }
        dirty = true;
    }

    public void flush() {
        JsonObject root = writeRoot();
        Path parent = configPath.getParent();
        Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            atomicReplace(temporary, configPath);
            dirty = false;
        } catch (IOException exception) {
            LunacyVisuals.LOGGER.error("Could not save configuration to {}", configPath, exception);
        }
    }

    public HudPosition hudPosition(String id, HudPosition fallback) {
        return hudPositions.getOrDefault(id, fallback);
    }

    public void setHudPosition(String id, HudPosition position) {
        hudPositions.put(id, position);
        markDirty();
    }

    public JsonObject snapshot() { return writeRoot().deepCopy(); }

    public void restore(JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject())
            throw new IllegalArgumentException("Profile must contain modules");
        JsonObject previous = snapshot();
        loading = true;
        try {
            hudPositions.clear();
            readRoot(root);
        } catch (RuntimeException failure) {
            hudPositions.clear();
            readRoot(previous);
            throw failure;
        } finally { loading = false; }
        markDirty();
    }

    private void readRoot(JsonObject root) {
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (schema > SCHEMA_VERSION) {
            LunacyVisuals.LOGGER.warn("Config schema {} is newer than supported schema {}", schema, SCHEMA_VERSION);
        }

        JsonObject moduleObject = childObject(root, "modules");
        for (Module module : modules.all()) {
            JsonObject saved = childObject(moduleObject, module.id());
            for (Setting<?> setting : module.settings()) {
                if (!saved.has(setting.key())) {
                    continue;
                }
                if (schema < 4 && module.id().equals("watermark") && setting.key().equals("accent")) {
                    continue;
                }
                try {
                    setting.readJson(saved.get(setting.key()));
                } catch (RuntimeException exception) {
                    LunacyVisuals.LOGGER.warn("Ignoring invalid {}.{} config value", module.id(), setting.key());
                }
            }
            if (saved.has("enabled")) {
                module.setEnabled(saved.get("enabled").getAsBoolean());
            }
        }

        // Armor HUD used to be forced on by the startup profile. Keep it opt-in after migration.
        if (schema < 4) {
            modules.find("armor_hud").ifPresent(module -> module.setEnabled(false));
        }

        if (clientSettings != null) {
            JsonObject clientObject = childObject(root, "client");
            for (Setting<?> setting : clientSettings.all()) {
                if (!clientObject.has(setting.key())) continue;
                // Schema 3 replaces the discarded light-reference UI with the premium green/violet system.
                // Keep behavioural preferences, but migrate legacy presentation values to the new defaults.
                if (schema < 3 && switch (setting.key()) {
                    case "theme", "accent", "menu_backdrop", "hud_style", "shader_preset" -> true;
                    default -> false;
                }) continue;
                try {
                    setting.readJson(clientObject.get(setting.key()));
                } catch (RuntimeException exception) {
                    LunacyVisuals.LOGGER.warn("Ignoring invalid client.{} config value", setting.key());
                }
            }
        }

        JsonObject hudObject = childObject(root, "hud");
        hudObject.entrySet().forEach(entry -> {
            try {
                JsonObject saved = entry.getValue().getAsJsonObject();
                HudAnchor anchor = HudAnchor.valueOf(getString(saved, "anchor", HudAnchor.TOP_LEFT.name()));
                hudPositions.put(entry.getKey(), new HudPosition(
                        anchor,
                        getDouble(saved, "x", 8.0),
                        getDouble(saved, "y", 8.0),
                        getDouble(saved, "scale", 1.0)
                ));
            } catch (RuntimeException exception) {
                LunacyVisuals.LOGGER.warn("Ignoring invalid HUD position for {}", entry.getKey());
            }
        });
    }

    private JsonObject writeRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);

        JsonObject moduleObject = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject saved = new JsonObject();
            saved.addProperty("enabled", module.isEnabled());
            for (Setting<?> setting : module.settings()) {
                saved.add(setting.key(), setting.toJson());
            }
            moduleObject.add(module.id(), saved);
        }
        root.add("modules", moduleObject);

        if (clientSettings != null) {
            JsonObject clientObject = new JsonObject();
            for (Setting<?> setting : clientSettings.all()) {
                clientObject.add(setting.key(), setting.toJson());
            }
            root.add("client", clientObject);
        }

        JsonObject hudObject = new JsonObject();
        hudPositions.forEach((id, position) -> {
            JsonObject saved = new JsonObject();
            saved.addProperty("anchor", position.anchor().name());
            saved.addProperty("x", position.x());
            saved.addProperty("y", position.y());
            saved.addProperty("scale", position.scale());
            hudObject.add(id, saved);
        });
        root.add("hud", hudObject);
        return root;
    }

    private void backupCorruptedFile() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        String suffix = ".corrupt-" + Instant.now().toEpochMilli() + ".json";
        Path backup = configPath.resolveSibling("lunacy_visuals" + suffix);
        try {
            Files.move(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LunacyVisuals.LOGGER.error("Could not back up corrupted config {}", configPath, exception);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject childObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            return new JsonObject();
        }
        return parent.getAsJsonObject(key);
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }
}
