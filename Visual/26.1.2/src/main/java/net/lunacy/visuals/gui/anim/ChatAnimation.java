package net.lunacy.visuals.gui.anim;

public final class ChatAnimation {
    private static double progress = 0.0;
    private static long lastTime = System.currentTimeMillis();
    private static long lastMessageTime = 0;

    private ChatAnimation() {}

    public static void reset() {
        progress = 0.0;
        lastTime = System.currentTimeMillis();
    }

    public static void onMessageReceived() {
        lastMessageTime = System.currentTimeMillis();
    }

    public static double getMessageAnim() {
        long elapsed = System.currentTimeMillis() - lastMessageTime;
        if (elapsed < 0 || elapsed > 350) return 1.0;
        double p = elapsed / 350.0;
        return 1.0 - Math.pow(1.0 - p, 3);
    }

    public static double update(boolean targetOpen) {
        long now = System.currentTimeMillis();
        double dt = Math.clamp((now - lastTime) / 1000.0, 0.001, 0.05);
        lastTime = now;

        double speed = targetOpen ? 8.0 : 7.0;
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
