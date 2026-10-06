package net.lunacy.visuals.config;

import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Global appearance and renderer options which are not visual modules themselves. */
public final class ClientSettings {
    public enum ThemeMode { LIGHT, DARK }
    public enum MenuBackdrop { CLEAN, ARTWORK }
    public enum HudStyle { CLEAN, GLASS }
    public enum ShaderQuality { PERFORMANCE, BALANCED, QUALITY }
    public enum ShaderPreset { SOFT, LIQUID_GLASS, NEON }

    private final List<Setting<?>> settings = new ArrayList<>();

    private final EnumSetting<ThemeMode> theme = add(new EnumSetting<>(
            "theme", "Theme", "Dark Lunacy glass or a softer charcoal variant",
            ThemeMode.DARK, ThemeMode.class));
    private final ColorSetting accent = add(new ColorSetting(
            "accent", "Accent", "Emerald-to-violet UI and HUD accent",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.75)));
    private final DoubleSetting uiScale = add(new DoubleSetting(
            "ui_scale", "Interface scale", "Scale of Lunacy menus", 1.0, 0.80, 1.20, 0.05));
    private final EnumSetting<MenuBackdrop> menuBackdrop = add(new EnumSetting<>(
            "menu_backdrop", "Menu backdrop", "Clean canvas or the bundled artwork",
            MenuBackdrop.ARTWORK, MenuBackdrop.class));
    private final BooleanSetting menuDimming = add(new BooleanSetting(
            "menu_blur", "World shading", "Darken the live world behind menus without blurring it", true));
    private final BooleanSetting reducedMotion = add(new BooleanSetting(
            "reduced_motion", "Reduced motion", "Disable parallax and shorten interface animations", false));
    private final EnumSetting<HudStyle> hudStyle = add(new EnumSetting<>(
            "hud_style", "HUD style", "Clean light cards or translucent glass panels",
            HudStyle.GLASS, HudStyle.class));
    private final EnumSetting<ShaderQuality> shaderQuality = add(new EnumSetting<>(
            "shader_quality", "Shader quality", "Controls cosmetic density and effect cadence",
            ShaderQuality.BALANCED, ShaderQuality.class));
    private final EnumSetting<ShaderPreset> shaderPreset = add(new EnumSetting<>(
            "shader_preset", "Shader preset", "Soft, liquid-glass, or neon presentation",
            ShaderPreset.NEON, ShaderPreset.class));
    private final BooleanSetting customShaders = add(new BooleanSetting(
            "custom_shaders", "Custom shader programs", "Compile bundled blur, border, liquid-glass and MSDF programs", true));
    private final BooleanSetting changelog = add(new BooleanSetting(
            "show_changelog", "Menu changelog", "Show the status and changelog cards on the title screen", false));

    private <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> all() { return Collections.unmodifiableList(settings); }
    public ThemeMode theme() { return theme.get(); }
    public ColorSetting accent() { return accent; }
    public double uiScale() { return uiScale.get(); }
    public MenuBackdrop menuBackdrop() { return menuBackdrop.get(); }
    public boolean menuDimming() { return menuDimming.get(); }
    public boolean reducedMotion() { return reducedMotion.get(); }
    public HudStyle hudStyle() { return hudStyle.get(); }
    public ShaderQuality shaderQuality() { return shaderQuality.get(); }
    public ShaderPreset shaderPreset() { return shaderPreset.get(); }
    public boolean customShaders() { return customShaders.get(); }
    public boolean showChangelog() { return changelog.get(); }
}
