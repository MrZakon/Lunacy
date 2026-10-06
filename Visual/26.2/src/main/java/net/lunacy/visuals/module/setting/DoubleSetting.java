package net.lunacy.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class DoubleSetting extends Setting<Double> {
    private final double minimum;
    private final double maximum;
    private final double step;

    public DoubleSetting(
            String key,
            String name,
            String description,
            double defaultValue,
            double minimum,
            double maximum,
            double step
    ) {
        super(key, name, description, defaultValue);
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum || step <= 0.0) {
            throw new IllegalArgumentException("Invalid numeric setting range");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double step() {
        return step;
    }

    @Override
    protected Double normalize(Double value) {
        double finite = Double.isFinite(value) ? value : defaultValue();
        double snapped = Math.round((finite - minimum) / step) * step + minimum;
        return Math.clamp(snapped, minimum, maximum);
    }

    @Override
    protected JsonElement encode(Double value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected Double decode(JsonElement element) {
        return element.getAsDouble();
    }
}
