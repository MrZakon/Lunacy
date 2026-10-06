package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;

/**
 * Low Fire and Custom Colored Fire on screen without shader dependencies.
 */
public final class LowFireModule extends Module {
    public enum FireTint {
        VANILLA(0xFFFFFFFF),
        EMERALD(0xFF42FFA7),
        VIOLET(0xFFA866FF),
        ICE_BLUE(0xFF4DE2FF),
        RED(0xFFFF4848);

        private final int argb;
        FireTint(int argb) { this.argb = argb; }
        public int argb() { return argb; }
    }

    private final BooleanSetting noFire = add(new BooleanSetting(
            "no_fire", "No Fire", "Completely remove screen fire overlay", false));
    private final DoubleSetting height = add(new DoubleSetting(
            "height", "Fire Height", "Vertical screen coverage (lower is less obstructive)", 0.32, 0.05, 0.9, 0.05));
    private final EnumSetting<FireTint> tint = add(new EnumSetting<>(
            "tint", "Fire Color", "Screen fire tint color", FireTint.EMERALD, FireTint.class));

    public LowFireModule() {
        super("low_fire", "Low & Colored Fire", "Lower screen fire and customizable flame colors",
                ModuleCategory.RENDER_FX);
        setEnabled(true);
    }

    @Override public String displayValue() { return noFire.get() ? "OFF" : tint.get().name(); }

    public boolean noFire() { return noFire.get(); }

    public float heightOffset() {
        return (float) (0.9 - height.get() * 0.9);
    }

    public int tintRgb() {
        return tint.get().argb();
    }
}