package net.lunacy.visuals.module;

import net.lunacy.visuals.event.EventBus;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModuleRegistryTest {
    @Test
    void registersAllUniqueVisualModules() {
        ModuleManager manager = new ModuleManager(new EventBus());
        ModuleRegistry.registerAll(manager);

        assertEquals(35, manager.all().size());
        assertEquals(35, new HashSet<>(manager.all().stream().map(Module::id).toList()).size());
        assertFalse(manager.all().stream().anyMatch(module -> module.id().equals("keybinds_hud")));
        assertFalse(manager.all().stream().anyMatch(module -> module.settings().stream()
                .anyMatch(setting -> setting.key().equals("keybind"))));
        assertEquals("body_aura", manager.find("body_aura").orElseThrow().id());
    }
}
