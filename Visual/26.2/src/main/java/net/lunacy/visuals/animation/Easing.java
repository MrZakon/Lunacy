package net.lunacy.visuals.animation;

import java.util.function.DoubleUnaryOperator;

public enum Easing implements DoubleUnaryOperator {
    LINEAR(value -> value),
    EASE_OUT_CUBIC(value -> 1.0 - Math.pow(1.0 - value, 3.0)),
    EASE_IN_OUT_QUAD(value -> value < 0.5
            ? 2.0 * value * value
            : 1.0 - Math.pow(-2.0 * value + 2.0, 2.0) / 2.0),
    EASE_OUT_ELASTIC(value -> {
        if (value == 0.0 || value == 1.0) {
            return value;
        }
        double period = (2.0 * Math.PI) / 3.0;
        return Math.pow(2.0, -10.0 * value) * Math.sin((value * 10.0 - 0.75) * period) + 1.0;
    });

    private final DoubleUnaryOperator function;

    Easing(DoubleUnaryOperator function) {
        this.function = function;
    }

    @Override
    public double applyAsDouble(double operand) {
        return function.applyAsDouble(Math.clamp(operand, 0.0, 1.0));
    }
}
