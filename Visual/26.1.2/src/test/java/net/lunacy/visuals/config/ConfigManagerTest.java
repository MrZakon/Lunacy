package net.lunacy.visuals.config;

import net.lunacy.visuals.event.Event;
import net.lunacy.visuals.event.EventBus;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.ModuleManager;
import net.lunacy.visuals.module.setting.DoubleSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    // На Windows антивирус может кратко удерживать атомарно заменённый JSON и сорвать cleanup JUnit.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path temporaryDirectory;

    @Test
    void roundTripsModuleStateAndClampsValues() {
        Path path = temporaryDirectory.resolve("lunacy_visuals.json");
        ModuleManager firstModules = new ModuleManager(new EventBus());
        TestModule first = firstModules.register(new TestModule());
        ConfigManager firstConfig = new ConfigManager(path, firstModules);
        firstConfig.load();
        first.amount.set(10.7);
        first.setEnabled(true);
        firstConfig.markDirty();
        firstConfig.flush();

        ModuleManager secondModules = new ModuleManager(new EventBus());
        TestModule second = secondModules.register(new TestModule());
        ConfigManager secondConfig = new ConfigManager(path, secondModules);
        secondConfig.load();

        assertTrue(second.isEnabled());
        assertEquals(10.0, second.amount.get());
        second.setEnabled(false);
        assertFalse(second.isEnabled());
    }

    @Test
    void roundTripsGlobalClientSettingsWithoutAddingModules() {
        Path path = temporaryDirectory.resolve("lunacy_global_settings.json");
        ModuleManager firstModules = new ModuleManager(new EventBus());
        ClientSettings firstSettings = new ClientSettings();
        ConfigManager firstConfig = new ConfigManager(path, firstModules, firstSettings);
        firstConfig.load();
        firstSettings.all().stream().filter(setting -> setting.key().equals("theme")).findFirst().orElseThrow()
                .readJson(new com.google.gson.JsonPrimitive("dark"));
        firstSettings.all().stream().filter(setting -> setting.key().equals("ui_scale")).findFirst().orElseThrow()
                .readJson(new com.google.gson.JsonPrimitive(1.15));
        firstConfig.flush();

        ClientSettings restored = new ClientSettings();
        ConfigManager restoredConfig = new ConfigManager(path, new ModuleManager(new EventBus()), restored);
        restoredConfig.load();

        assertEquals(ClientSettings.ThemeMode.DARK, restored.theme());
        assertEquals(1.15, restored.uiScale(), 0.000001);
    }

    @Test
    void migratesLegacyPresentationWithoutDroppingBehaviourPreferences() throws Exception {
        Path path = temporaryDirectory.resolve("lunacy_legacy_settings.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 2,
                  "modules": {},
                  "client": {
                    "theme": "light",
                    "menu_backdrop": "clean",
                    "hud_style": "clean",
                    "shader_preset": "soft",
                    "ui_scale": 1.15,
                    "reduced_motion": true
                  },
                  "hud": {}
                }
                """);

        ClientSettings migrated = new ClientSettings();
        new ConfigManager(path, new ModuleManager(new EventBus()), migrated).load();

        assertEquals(ClientSettings.ThemeMode.DARK, migrated.theme());
        assertEquals(ClientSettings.MenuBackdrop.ARTWORK, migrated.menuBackdrop());
        assertEquals(ClientSettings.HudStyle.GLASS, migrated.hudStyle());
        assertEquals(ClientSettings.ShaderPreset.NEON, migrated.shaderPreset());
        assertEquals(1.15, migrated.uiScale(), 0.000001);
        assertTrue(migrated.reducedMotion());
    }

    private static final class TestModule extends Module {
        private final DoubleSetting amount = add(new DoubleSetting(
                "amount", "Amount", "Test amount", 1.0, 0.0, 10.0, 0.5
        ));

        private TestModule() {
            super("test", "Test", "Test module", ModuleCategory.SETTINGS);
        }

        @Subscribe
        private void onTest(TestEvent ignored) {
        }
    }

    private static final class TestEvent implements Event {
    }
}
