package net.lunacy.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> enumType;

    public EnumSetting(String key, String name, String description, E defaultValue, Class<E> enumType) {
        super(key, name, description, defaultValue);
        this.enumType = enumType;
    }

    public E[] values() {
        return enumType.getEnumConstants();
    }

    @Override
    protected JsonElement encode(E value) {
        return new JsonPrimitive(value.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected E decode(JsonElement element) {
        String requested = element.getAsString();
        for (E value : values()) {
            if (value.name().equalsIgnoreCase(requested)) {
                return value;
            }
        }
        return defaultValue();
    }
}
