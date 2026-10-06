package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.config.ClientSettings;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;

/** Shared palette and small drawing language used by every Lunacy screen. */
public final class LunacyTheme {
    private LunacyTheme() {}

    public static boolean dark() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime != null && runtime.settings().theme() == ClientSettings.ThemeMode.DARK;
    }

    public static int background() { return dark() ? 0xFF050810 : 0xFF101426; }
    public static int backgroundAlt() { return dark() ? 0xFF0B061A : 0xFF181030; }
    public static int surface() { return dark() ? 0xDD101524 : 0xE3182236; }
    public static int surfaceStrong() { return dark() ? 0xFF16203A : 0xFF263654; }
    public static int text() { return 0xFFF1F4FF; }
    public static int muted() { return dark() ? 0xFF7683AA : 0xFF95A0C6; }
    public static int faint() { return dark() ? 0xFF3D4868 : 0xFF5F6D94; }
    public static int line() { return dark() ? 0xFF2E4573 : 0xFF435A88; }
    public static int softLine() { return dark() ? 0x525CE1E6 : 0x627AEEF2; }
    public static int success() { return 0xFF5CE1E6; }
    public static int danger() { return 0xFFFF5C77; }

    public static int accent() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? LunacyMenuChrome.GREEN
                : runtime.settings().accent().get().colorAt(0, System.currentTimeMillis());
    }

    public static void canvas(DrawContext context, int width, int height) {
        context.fillGradient(0, 0, width, height, background(), backgroundAlt());
    }

    public static void card(DrawContext context, double x, double y, double width, double height, double radius) {
        UiRenderer.shadow(context, x, y, width, height, radius, ColorUtil.withAlpha(accent(), dark() ? 42 : 24));
        UiRenderer.roundedRect(context, x, y, width, height, radius, surface());
        UiRenderer.roundedBorder(context, x, y, width, height, radius, 1, softLine());
    }

    public static void eyebrow(DrawContext context, String value, double x, double y) {
        UiRenderer.spacedText(context, value.toUpperCase(), x, y, faint(), 1.0, 1);
    }

    public static void title(DrawContext context, String value, double x, double y, double scale) {
        UiRenderer.textScaled(context, value, x, y, text(), false, scale);
    }
}
