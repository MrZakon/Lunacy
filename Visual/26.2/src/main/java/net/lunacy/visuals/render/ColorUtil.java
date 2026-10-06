package net.lunacy.visuals.render;

public final class ColorUtil {
    private ColorUtil() {}

    public static int argb(int alpha, int red, int green, int blue) {
        return (Math.clamp(alpha, 0, 255) << 24)
                | (Math.clamp(red, 0, 255) << 16)
                | (Math.clamp(green, 0, 255) << 8)
                | Math.clamp(blue, 0, 255);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FF_FFFF);
    }

    public static int mix(int first, int second, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 255) + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * t);
        int r = Math.round(((first >>> 16) & 255) + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * t);
        int g = Math.round(((first >>> 8) & 255) + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return argb(a, r, g, b);
    }

    public static int rainbow(long timeMillis, float saturation, float brightness) {
        float hue = (timeMillis % 6000L) / 6000.0F;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FF_FFFF);
    }
}
