package net.lunacy.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ColorSetting extends Setting<ColorValue> {
    public ColorSetting(String key, String name, String description, ColorValue defaultValue) {
        super(key, name, description, defaultValue);
    }

    @Override
    protected JsonElement encode(ColorValue value) {
        JsonObject object = new JsonObject();
        object.addProperty("primary", String.format("#%08X", value.primaryArgb()));
        object.addProperty("secondary", String.format("#%08X", value.secondaryArgb()));
        object.addProperty("rainbow", value.rainbow());
        object.addProperty("rainbowSpeed", value.rainbowSpeed());
        return object;
    }

    @Override
    protected ColorValue decode(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return ColorValue.solid(parseColor(element.getAsString(), defaultValue().primaryArgb()));
        }
        JsonObject object = element.getAsJsonObject();
        return new ColorValue(
                parseColor(getString(object, "primary", null), defaultValue().primaryArgb()),
                parseColor(getString(object, "secondary", null), defaultValue().secondaryArgb()),
                getBoolean(object, "rainbow", defaultValue().rainbow()),
                getDouble(object, "rainbowSpeed", defaultValue().rainbowSpeed())
        );
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            String normalized = value.startsWith("#") ? value.substring(1) : value;
            return (int) Long.parseUnsignedLong(normalized, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }
}
