package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.EnumSetting;

/**
 * Minimalist, non-obstructive totem pop animations and custom neon particle bursts.
 */
public final class TotemPopModule extends Module {
    public enum PopMode {
        MINIMAL,
        OFF,
        VANILLA
    }

    private final EnumSetting<PopMode> mode = add(new EnumSetting<>(
            "mode", "Totem Style", "Viewport totem popup style", PopMode.MINIMAL, PopMode.class));
    private final BooleanSetting customParticles = add(new BooleanSetting(
            "custom_particles", "Neon Particles", "Replace vanilla totem clouds with Lunacy neon bursts", true));

    public TotemPopModule() {
        super("totem_pop", "Minimal Totem Pop", "Prevent screen obstruction when popping totems of undying",
                ModuleCategory.RENDER_FX);
        setEnabled(true);
    }

    @Override public String displayValue() { return mode.get().name(); }

    public PopMode mode() { return mode.get(); }
    public boolean customParticles() { return customParticles.get(); }
}