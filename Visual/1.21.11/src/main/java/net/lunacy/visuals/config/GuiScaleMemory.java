package net.lunacy.visuals.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Remembers the vanilla GUI Scale ("Интерфейс") across launches.
 * Options.load() runs before Lunacy's config manager, so this lives in a
 * tiny sidecar that the mixin can read at the earliest possible moment.
 */
public final class GuiScaleMemory {
    public static final int UNSET = -1;
    public static final int FIRST_LAUNCH_DEFAULT = 2;
    public static final int MIN = 0;
    public static final int MAX = 32;
    static final String FILE_NAME = "lunacy_gui_scale.txt";

    private GuiScaleMemory() {
    }

    public static Path path(Path configDir) {
        return configDir.resolve(FILE_NAME);
    }

    public static int read(Path configDir) {
        Path file = path(configDir);
        if (!Files.isRegularFile(file)) {
            return UNSET;
        }
        try {
            int value = Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).trim());
            return isValid(value) ? value : UNSET;
        } catch (Exception ignored) {
            return UNSET;
        }
    }

    public static void write(Path configDir, int value) {
        if (!isValid(value)) {
            return;
        }
        try {
            Files.createDirectories(configDir);
            Path file = path(configDir);
            Path temporary = configDir.resolve(FILE_NAME + ".tmp");
            Files.writeString(temporary, Integer.toString(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean isValid(int value) {
        return value >= MIN && value <= MAX;
    }
}
