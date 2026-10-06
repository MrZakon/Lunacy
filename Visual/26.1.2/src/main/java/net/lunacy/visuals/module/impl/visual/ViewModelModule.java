package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;

public final class ViewModelModule extends Module {
    public enum SwingStyle { VANILLA, SMOOTH, SWIPE, EXHIBITION }
    private final DoubleSetting offsetX = add(new DoubleSetting("offset_x", "Offset X", "Horizontal hand offset", 0, -2, 2, 0.05));
    private final DoubleSetting offsetY = add(new DoubleSetting("offset_y", "Offset Y", "Vertical hand offset", 0, -2, 2, 0.05));
    private final DoubleSetting offsetZ = add(new DoubleSetting("offset_z", "Offset Z", "Depth hand offset", 0, -2, 2, 0.05));
    private final DoubleSetting scale = add(new DoubleSetting("scale", "Scale", "First-person item scale", 1, 0.55, 1.45, 0.05));
    private final DoubleSetting tilt = add(new DoubleSetting("tilt", "Tilt", "Static hand roll", 0, -90, 90, 1));
    private final EnumSetting<SwingStyle> swing = add(new EnumSetting<>(
            "swing", "Swing", "Additional first-person swing motion", SwingStyle.SMOOTH, SwingStyle.class));

    public ViewModelModule() {
        super("view_model", "Hands & ViewModel", "Per-hand position, scale, tilt and four swing styles",
                ModuleCategory.RENDER_FX);
    }

    @Override public String displayValue() { return swing.get().name(); }
    public float offsetX() { return offsetX.get().floatValue(); }
    public float offsetY() { return offsetY.get().floatValue(); }
    public float offsetZ() { return offsetZ.get().floatValue(); }
    public float scale() { return scale.get().floatValue(); }
    public float tilt() { return tilt.get().floatValue(); }
    public SwingStyle swingStyle() { return swing.get(); }
}
