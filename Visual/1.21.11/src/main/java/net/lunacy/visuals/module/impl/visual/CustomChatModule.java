package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.DoubleSetting;

public final class CustomChatModule extends Module {
    private final DoubleSetting backgroundAlpha = add(new DoubleSetting(
            "background_alpha", "Background", "Chat panel opacity", 0.42, 0, 0.9, 0.01));
    private final BooleanSetting gradientNames = add(new BooleanSetting(
            "gradient_names", "Animated accent", "Animate the edge accent while chat is visible", true));
    private final BooleanSetting smooth = add(new BooleanSetting(
            "smooth_scroll", "Soft edges", "Use rounded clean borders around chat", true));

    public CustomChatModule() {
        super("custom_chat", "Custom Chat HUD", "Theme-aware chat surface and animated accent",
                ModuleCategory.HUD);
    }

    public float backgroundAlpha() { return backgroundAlpha.get().floatValue(); }
    public boolean gradientNames() { return gradientNames.get(); }
    public boolean smoothEdges() { return smooth.get(); }
}
