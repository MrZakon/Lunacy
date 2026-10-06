package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.ColorUtil;

public final class HitColorModule extends Module {
    public enum Style { SOLID, PULSE, GRADIENT }

    private final ColorSetting color = add(new ColorSetting(
            "color", "Damage color", "Color applied to damaged entities",
            new ColorValue(0xFFFF5C77, 0xFFA66BFF, false, 0.8)));
    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Animation", "Static, pulsing, or gradient damage tint", Style.GRADIENT, Style.class));
    private final DoubleSetting intensity = add(new DoubleSetting(
            "intensity", "Intensity", "Damage tint opacity", 0.68, 0.1, 1, 0.05));
    private final DoubleSetting speed = add(new DoubleSetting(
            "speed", "Speed", "Pulse and gradient speed", 1, 0.15, 3, 0.05));

    public HitColorModule() {
        super("hit_color", "Damage Color", "Accurate colored entity damage flash",
                ModuleCategory.RENDER_FX);
    }

    @Override public String displayValue() { return style.get().name(); }

    public int mixColor() {
        long now = System.currentTimeMillis();
        double phase = CosmeticParticleUtil.wrap(now / (1200.0 / speed.get()));
        int rgb = switch (style.get()) {
            case SOLID -> color.get().colorAt(0, now);
            case GRADIENT -> color.get().colorAt(0.5 - Math.cos(phase * Math.PI * 2) * 0.5, now);
            case PULSE -> ColorUtil.mix(color.get().colorAt(0, now), 0xFFFFFFFF,
                    (float) (0.08 + 0.22 * (0.5 + 0.5 * Math.sin(phase * Math.PI * 2))));
        };
        return ColorUtil.withAlpha(rgb, (int) Math.round(intensity.get() * 255));
    }
}
