package net.lunacy.visuals.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiScaleMemoryTest {
    @TempDir
    Path configDir;

    @Test
    void roundTripsChosenScale() {
        assertEquals(GuiScaleMemory.UNSET, GuiScaleMemory.read(configDir));
        GuiScaleMemory.write(configDir, 3);
        assertEquals(3, GuiScaleMemory.read(configDir));
        GuiScaleMemory.write(configDir, 1);
        assertEquals(1, GuiScaleMemory.read(configDir));
        GuiScaleMemory.write(configDir, 0);
        assertEquals(0, GuiScaleMemory.read(configDir));
    }

    @Test
    void ignoresCorruptAndOutOfRangeValues() throws Exception {
        Files.writeString(GuiScaleMemory.path(configDir), "nope");
        assertEquals(GuiScaleMemory.UNSET, GuiScaleMemory.read(configDir));
        Files.writeString(GuiScaleMemory.path(configDir), "99");
        assertEquals(GuiScaleMemory.UNSET, GuiScaleMemory.read(configDir));
        GuiScaleMemory.write(configDir, -2);
        assertEquals(GuiScaleMemory.UNSET, GuiScaleMemory.read(configDir));
    }
}
