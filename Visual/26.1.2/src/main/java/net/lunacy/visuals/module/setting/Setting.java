package net.lunacy.visuals.module.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Setting<T> {
    private final String key;
    private final String name;
    private final String description;
    private final T defaultValue;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private T value;
    private BooleanSupplier visible = () -> true;

    protected Setting(String key, String name, String description, T defaultValue) {
        this.key = requireKey(key);
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        value = defaultValue;
    }

    public final String key() {
        return key;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final T get() {
        return value;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final void set(T requestedValue) {
        T normalized = normalize(Objects.requireNonNull(requestedValue, "requestedValue"));
        if (Objects.equals(value, normalized)) {
            return;
        }
        value = normalized;
        listeners.forEach(listener -> listener.accept(normalized));
    }

    public final void reset() {
        set(defaultValue);
    }

    public final boolean isVisible() {
        return visible.getAsBoolean();
    }

    public final Setting<T> visibleWhen(BooleanSupplier condition) {
        visible = Objects.requireNonNull(condition, "condition");
        return this;
    }

    public final Setting<T> onChanged(Consumer<T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public final JsonElement toJson() {
        return encode(value);
    }

    public final void readJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        set(decode(element));
    }

    protected T normalize(T value) {
        return value;
    }

    protected abstract JsonElement encode(T value);

    protected abstract T decode(JsonElement element);

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!key.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Setting key must be snake_case: " + key);
        }
        return key;
    }
}
