package net.lunacy.visuals.module.setting;

public record ColorValue(int primaryArgb, int secondaryArgb, boolean rainbow, double rainbowSpeed) {
    public ColorValue {
        rainbowSpeed = Math.clamp(rainbowSpeed, 0.05, 5.0);
    }

    public static ColorValue solid(int argb) {
        return new ColorValue(argb, argb, false, 1.0);
    }

    public int colorAt(double progress, long timeMillis) {
        if (rainbow) {
            float hue = (float) ((timeMillis / 10_000.0 * rainbowSpeed + progress) % 1.0);
            return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) & 0x00FFFFFF;
        }
        double clamped = Math.clamp(progress, 0.0, 1.0);
        int a = lerp(primaryArgb >>> 24, secondaryArgb >>> 24, clamped);
        int r = lerp(primaryArgb >>> 16 & 0xFF, secondaryArgb >>> 16 & 0xFF, clamped);
        int g = lerp(primaryArgb >>> 8 & 0xFF, secondaryArgb >>> 8 & 0xFF, clamped);
        int b = lerp(primaryArgb & 0xFF, secondaryArgb & 0xFF, clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerp(int start, int end, double progress) {
        return Math.clamp((int) Math.round(start + (end - start) * progress), 0, 255);
    }
}
