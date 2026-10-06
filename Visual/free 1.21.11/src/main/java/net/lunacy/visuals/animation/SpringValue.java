package net.lunacy.visuals.animation;

/** Устойчивая полуаналитическая пружина с ограниченным шагом интегрирования. */
public final class SpringValue {
    private double value;
    private double velocity;
    private double target;
    private double stiffness;
    private double damping;

    public SpringValue(double initialValue, double stiffness, double damping) {
        value = initialValue;
        target = initialValue;
        this.stiffness = Math.max(0.0, stiffness);
        this.damping = Math.max(0.0, damping);
    }

    public double update(double deltaSeconds) {
        int steps = Math.max(1, (int) Math.ceil(deltaSeconds / (1.0 / 120.0)));
        double step = Math.min(0.1, Math.max(0.0, deltaSeconds)) / steps;
        for (int index = 0; index < steps; index++) {
            double acceleration = (target - value) * stiffness - velocity * damping;
            velocity += acceleration * step;
            value += velocity * step;
        }
        if (Math.abs(value - target) < 1.0E-5 && Math.abs(velocity) < 1.0E-5) {
            value = target;
            velocity = 0.0;
        }
        return value;
    }

    public double value() {
        return value;
    }

    public double velocity() {
        return velocity;
    }

    public void setTarget(double target) {
        this.target = target;
    }

    public void configure(double stiffness, double damping) {
        this.stiffness = Math.max(0.0, stiffness);
        this.damping = Math.max(0.0, damping);
    }

    public void snap(double value) {
        this.value = value;
        target = value;
        velocity = 0.0;
    }
}
