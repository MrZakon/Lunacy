package net.lunacy.visuals.module.impl.hud;

/** Общий delta-time текущего HUD-кадра. */
public final class ClientRuntimeFrame {
    private static long previous = System.nanoTime();
    private static double delta = 1.0 / 60.0;

    private ClientRuntimeFrame() {}

    public static void update() {
        long now = System.nanoTime();
        delta = Math.clamp((now - previous) / 1_000_000_000.0, 0.0, 0.1);
        previous = now;
    }

    public static double delta() { return delta; }
}
