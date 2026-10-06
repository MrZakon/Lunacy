package net.lunacy.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, String name, String description, boolean defaultValue) {
        super(key, name, description, defaultValue);
    }

    @Override
    protected JsonElement encode(Boolean value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected Boolean decode(JsonElement element) {
        return element.getAsBoolean();
    }
}
