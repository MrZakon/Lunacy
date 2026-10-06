package net.lunacy.visuals.localization;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.Setting;
import net.minecraft.client.resources.language.I18n;
import java.util.Locale;

/** Live translations backed by Minecraft's currently selected language. */
public final class LunacyI18n {
    private static final String PREFIX = "lunacyvisuals.";

    private LunacyI18n() {}

    public static String tr(String path, Object... arguments) {
        return I18n.get(PREFIX + path, arguments);
    }

    public static String trOr(String path, String fallback, Object... arguments) {
        String key = PREFIX + path;
        String translated = I18n.get(key, arguments);
        return translated.equals(key) ? fallback : translated;
    }

    public static String moduleName(Module module) {
        return trOr("module." + module.id() + ".name", module.name());
    }

    public static String moduleDescription(Module module) {
        return trOr("module." + module.id() + ".description", module.description());
    }

    public static String category(ModuleCategory category) {
        return trOr("category." + category.name().toLowerCase(Locale.ROOT), category.displayName());
    }

    public static String settingName(Setting<?> setting) {
        return trOr("setting." + setting.key() + ".name", setting.name());
    }

    public static String settingDescription(Setting<?> setting) {
        return trOr("setting." + setting.key() + ".description", setting.description());
    }

    public static String value(Object value) {
        if (value == null) return "";
        String raw = value instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(value);
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? raw : trOr("value." + normalized, raw.replace('_', ' '));
    }
}
