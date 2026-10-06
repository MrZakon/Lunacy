package net.lunacy.visuals.module;

import net.lunacy.visuals.module.impl.hud.*;
import net.lunacy.visuals.module.impl.visual.*;

/** Декларация визуальных модулей клиента — FREE edition. */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static void registerAll(ModuleManager modules) {
        // FREE: watermark plus four visual modules.
        modules.register(new FullbrightModule());
        modules.register(new LowFireModule());
        modules.register(new CustomChatModule());
        modules.register(new GhostAfterimagesModule());
        modules.register(new WatermarkModule());
    }
}
