package net.lunacy.visuals.config;
import net.lunacy.visuals.module.ModuleManager;
import net.lunacy.visuals.module.setting.*;
import com.google.gson.JsonPrimitive;
public final class AppearancePresets {
    public enum Preset { ECLIPSE, SAKURA, FROST, MINIMAL }
    public static void apply(Preset preset, ModuleManager modules, ClientSettings settings) {
        int first = switch (preset) { case ECLIPSE -> 0xFFA878FF; case SAKURA -> 0xFFFF92B9; case FROST -> 0xFF7CDFFF; case MINIMAL -> 0xFFBCC8CE; };
        int second = switch (preset) { case ECLIPSE -> 0xFF5143A8; case SAKURA -> 0xFFFFD9ED; case FROST -> 0xFFE4FCFF; case MINIMAL -> 0xFFEEF4F4; };
        ColorValue colors = new ColorValue(first, second, false, .6);
        settings.accent().set(colors);
        for (var setting : settings.all()) {
            if (setting.key().equals("theme")) setting.readJson(new JsonPrimitive("dark"));
            if (setting.key().equals("hud_style")) setting.readJson(new JsonPrimitive(preset == Preset.MINIMAL ? "clean" : "glass"));
            if (setting.key().equals("shader_preset")) setting.readJson(new JsonPrimitive(
                    preset == Preset.MINIMAL ? "soft" : preset == Preset.FROST ? "liquid_glass" : "neon"));
        }
        for (var module : modules.all()) {
            for (var setting : module.settings()) if (setting instanceof ColorSetting color) color.set(colors);
            if (java.util.Set.of("weapon_trails", "landing_fx", "companion_spirit", "biome_atmosphere", "animated_hotbar", "view_model").contains(module.id()))
                module.setEnabled(preset != Preset.MINIMAL || module.id().equals("animated_hotbar"));
            if (preset == Preset.MINIMAL && module.category() == net.lunacy.visuals.module.ModuleCategory.COSMETICS) module.setEnabled(false);
            if (module.id().equals("ambience")) {
                for (var setting : module.settings()) {
                    if (setting.key().equals("strength")) setting.readJson(new JsonPrimitive(preset == Preset.MINIMAL ? 0 : .06));
                    if (setting.key().equals("galaxy_sky")) setting.readJson(new JsonPrimitive(preset == Preset.ECLIPSE));
                }
                module.setEnabled(preset != Preset.MINIMAL);
            }
        }
    }
}
