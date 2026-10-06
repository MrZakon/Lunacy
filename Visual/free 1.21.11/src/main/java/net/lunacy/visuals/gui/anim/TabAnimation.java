package net.lunacy.visuals.gui.anim;

public final class TabAnimation {
    private static double progress = 0.0;
    private static long lastTime = System.currentTimeMillis();

    private TabAnimation() {}

    public static double update(boolean targetOpen) {
        long now = System.currentTimeMillis();
        double dt = Math.clamp((now - lastTime) / 1000.0, 0.001, 0.05);
        lastTime = now;

        double speed = targetOpen ? 8.5 : 7.5;
        if (targetOpen) {
            progress = Math.min(1.0, progress + dt * speed);
        } else {
            progress = Math.max(0.0, progress - dt * speed);
        }
        return getEased();
    }

    public static double getProgress() {
        return progress;
    }

    public static double getEased() {
        double p = 1.0 - progress;
        return 1.0 - (p * p * p);
    }
}
