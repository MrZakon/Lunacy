package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared cinematic chrome ported from the premium menu language to modern Yarn APIs. */
public final class LunacyMenuChrome {
    public static final int GREEN = 0xFF5CE1E6;
    public static final int GREEN_BRIGHT = 0xFF8AFFFF;
    public static final int VIOLET = 0xFFB18CFF;
    public static final int VIOLET_DARK = 0xFF7A4EEB;
    public static final int INK = 0xFF05080E;
    public static final int TEXT = 0xFFF1F4FF;
    public static final int MUTED = 0xFF8392AB;
    public static final int FAINT = 0xFF586782;
    public static final int LINE = 0x384C7AE0;
    public static final int DANGER = 0xFFFF5C77;

    private static final Identifier ARTWORK = id("mainmenu/bg-tower-clean.png");
    private static final double ARTWORK_WIDTH = 1412;
    private static final double ARTWORK_HEIGHT = 1114;
    private static final Identifier LOGO = id("textures/gui/lv_logo.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private LunacyMenuChrome() {}

    public static Viewport viewport(int screenWidth, int screenHeight, double designWidth, double designHeight) {
        double scale = Math.min(screenWidth / designWidth, screenHeight / designHeight);
        return new Viewport(scale, (screenWidth - designWidth * scale) / 2.0,
                (screenHeight - designHeight * scale) / 2.0, designWidth, designHeight);
    }

    /** A true modal viewport: it keeps a visible border around the workspace at every GUI scale. */
    public static Viewport windowViewport(int screenWidth, int screenHeight,
                                          double designWidth, double designHeight) {
        double margin = Math.clamp(Math.min(screenWidth, screenHeight) * 0.055, 14.0, 34.0);
        double scale = Math.min(1.0, Math.min(
                Math.max(1.0, screenWidth - margin * 2.0) / designWidth,
                Math.max(1.0, screenHeight - margin * 2.0) / designHeight));
        return new Viewport(scale, (screenWidth - designWidth * scale) / 2.0,
                (screenHeight - designHeight * scale) / 2.0, designWidth, designHeight);
    }

    public static void background(DrawContext context, int width, int height, int mouseX, int mouseY,
                                  boolean overWorld) {
        if (overWorld) {
            worldBackdrop(context, width, height);
        } else {
            double parallaxX = ClientRuntime.get().settings().reducedMotion() ? 0
                    : (mouseX / (double) Math.max(1, width) - 0.5) * 16;
            double parallaxY = ClientRuntime.get().settings().reducedMotion() ? 0
                    : (mouseY / (double) Math.max(1, height) - 0.5) * 10;
            double cover = Math.max(width / ARTWORK_WIDTH, height / ARTWORK_HEIGHT) * 1.08;
            double artWidth = ARTWORK_WIDTH * cover;
            double artHeight = ARTWORK_HEIGHT * cover;
            UiRenderer.textureInset(context, ARTWORK,
                    (width - artWidth) / 2.0 - parallaxX,
                    (height - artHeight) / 2.0 - parallaxY,
                    artWidth, artHeight, (int) ARTWORK_WIDTH, (int) ARTWORK_HEIGHT, 2);
            context.fillGradient(0, 0, width, height, 0x4007091B, 0x73050212);
        }

        UiRenderer.horizontalGradient(context, 0, 0, width, height, 0xA0050810, 0x1A080415);
    }

    /** Exact background transform used by the supplied CustomMainMenuScreen. */
    public static void titleBackground(DrawContext context, int width, int height,
                                       int mouseX, int mouseY) {
        double pointerX = Math.clamp((mouseX - width / 2.0) / Math.max(1.0, width / 2.0), -1.0, 1.0);
        double pointerY = Math.clamp((mouseY - height / 2.0) / Math.max(1.0, height / 2.0), -1.0, 1.0);
        if (ClientRuntime.get().settings().reducedMotion()) {
            pointerX = 0;
            pointerY = 0;
        }
        double cover = Math.max(width / ARTWORK_WIDTH, height / ARTWORK_HEIGHT) * 1.07;
        double artWidth = ARTWORK_WIDTH * cover;
        double artHeight = ARTWORK_HEIGHT * cover;
        double offsetX = -pointerX * Math.min(Math.max(0, (artWidth - width) / 2.0), 42);
        double offsetY = -pointerY * Math.min(Math.max(0, (artHeight - height) / 2.0), 26);
        UiRenderer.textureInset(context, ARTWORK,
                (width - artWidth) / 2.0 + offsetX,
                (height - artHeight) / 2.0 + offsetY,
                artWidth, artHeight, (int) ARTWORK_WIDTH, (int) ARTWORK_HEIGHT, 2);
        UiRenderer.horizontalGradient(context, 0, 0, width, height, 0x8C050914, 0x00050914);
    }

    /** Keeps the live world readable underneath menus and intentionally never invokes Minecraft blur. */
    public static void worldBackdrop(DrawContext context, int width, int height) {
        boolean dim = ClientRuntime.get().settings().menuDimming();
        context.fillGradient(0, 0, width, height,
                dim ? 0x6602050E : 0x1F02050E, dim ? 0x80050211 : 0x2A050211);
        UiRenderer.horizontalGradient(context, 0, 0, width, height,
                dim ? 0x6602050B : 0x1402050B, 0x14050210);
    }

    public static void topBar(DrawContext context, double designWidth) {
        centeredLogo(context, designWidth);
        drawClock(context, designWidth);
    }

    public static void centeredLogo(DrawContext context, double designWidth) {
        double size = 54;
        UiRenderer.texture(context, LOGO, designWidth / 2.0 - size / 2.0, 14,
                size, size, 128, 128);
    }

    public static void logo(DrawContext context, double x, double y, double size) {
        UiRenderer.texture(context, LOGO, x, y, size, size, 128, 128);
    }

    public static void cornerBrand(DrawContext context) {
        glass(context, 18, 18, 142, 42, 12, ColorUtil.withAlpha(GREEN, 105));
        logo(context, 24, 22, 34);
        UiRenderer.textScaled(context, "LUNACY", 64, 25, TEXT, false, 1.22);
        UiRenderer.spacedText(context, "VISUALS", 65, 44, GREEN, 0.56, 1);
    }

    /** Small enough to coexist with centered vanilla screen titles and their complete widget layouts. */
    public static void compactBrand(DrawContext context) {
        glass(context, 9, 8, 108, 31, 9, ColorUtil.withAlpha(GREEN, 90));
        logo(context, 12, 10, 27);
        UiRenderer.textScaled(context, "LUNACY", 43, 11, TEXT, false, 0.93);
        UiRenderer.spacedText(context, "VISUALS", 43, 25, GREEN, 0.45, 1);
    }

    /** Reference-style identity chip that stays clear of centered Minecraft titles. */
    public static void microBrand(DrawContext context) {
        glass(context, 9, 8, 82, 25, 9, ColorUtil.withAlpha(GREEN, 76));
        logo(context, 12, 10, 21);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "LUNACY", 38, 12.2,
                8.4, TEXT);
        UiRenderer.roundedRect(context, 38, 24.5, 34, 1, 0.5,
                ColorUtil.withAlpha(VIOLET, 105));
    }

    public static void workspaceBrand(DrawContext context, double designWidth) {
        UiRenderer.roundedRect(context, 46, 35, 34, 34, 9, 0xD90C1611);
        UiRenderer.texture(context, LOGO, 50, 39, 26, 26, 128, 128);
        UiRenderer.textScaled(context, "LUNACY", 91, 35, TEXT, false, 2.55);
        UiRenderer.spacedText(context, "VISUALS", 92, 62, GREEN, 0.78, 3);
        UiRenderer.horizontalGradient(context, 199, 65, Math.max(80, designWidth - 430), 1,
                ColorUtil.withAlpha(GREEN, 95), ColorUtil.withAlpha(VIOLET, 14));

        drawClock(context, designWidth);
    }

    private static void drawClock(DrawContext context, double designWidth) {
        LocalDateTime now = LocalDateTime.now();
        String clock = CLOCK.format(now);
        String language = MinecraftClient.getInstance().options.language;
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        String date = DateTimeFormatter.ofPattern("dd MMM yyyy", locale).format(now).toUpperCase(locale);
        double clockX = designWidth - 52 - UiRenderer.textWidth(clock) * 2.15;
        UiRenderer.textScaled(context, clock, clockX, 31, TEXT, false, 2.15);
        UiRenderer.spacedText(context, date,
                designWidth - 52 - UiRenderer.spacedTextWidth(date, 0.72, 1), 60,
                MUTED, 0.72, 1);
    }

    public static void glass(DrawContext context, double x, double y, double width, double height, double radius) {
        glass(context, x, y, width, height, radius, 0x3DFFFFFF);
    }

    public static void glass(DrawContext context, double x, double y, double width, double height,
                             double radius, int outlineColor) {
        UiRenderer.roundedRect(context, x, y, width, height, radius, outlineColor);
        UiRenderer.roundedRect(context, x + 1, y + 1, width - 2, height - 2,
                Math.max(1, radius - 1), 0xEE121B17);
        if (width > 28 && height > 8) {
            UiRenderer.roundedRect(context, x + radius, y + 1, width - radius * 2, 1,
                    0.5, 0x25FFFFFF);
        }
    }

    public static void avatar(DrawContext context, double x, double y, double size, boolean highlighted) {
        double frame = highlighted ? 4 : 2;
        UiRenderer.gradientRoundedRect(context, x - frame, y - frame, size + frame * 2, size + frame * 2,
                16, GREEN, VIOLET);
        UiRenderer.roundedRect(context, x - 1, y - 1, size + 2, size + 2, 13, 0xFF080E0B);
        // The supplied cosmic portal is the single client identity across custom menus.
        UiRenderer.texture(context, LOGO, x, y, size, size, 128, 128);
    }

    public static void button(DrawContext context, String label, String hint, double x, double y,
                              double width, double height, boolean primary, boolean danger,
                              boolean hovered) {
        int textColor = danger ? DANGER : TEXT;
        if (primary) {
            UiRenderer.shadow(context, x + 3, y + 4, width - 6, height,
                    12, ColorUtil.withAlpha(0xFF000000, hovered ? 105 : 78));
            UiRenderer.gradientRoundedRect(context, x, y, width, height, 12,
                    hovered ? 0xFF193A31 : 0xFF12271F,
                    hovered ? 0xFF342447 : 0xFF231A31);
            UiRenderer.roundedBorder(context, x, y, width, height, 12, 1,
                    ColorUtil.withAlpha(hovered ? GREEN_BRIGHT : GREEN, hovered ? 205 : 135));
            UiRenderer.roundedRect(context, x + 12, y + 1, width - 24, 1, 0.5, 0x5AFFFFFF);
            UiRenderer.horizontalGradient(context, x + 16, y + height - 2, width - 32, 2,
                    ColorUtil.withAlpha(GREEN, hovered ? 220 : 155),
                    ColorUtil.withAlpha(VIOLET, hovered ? 220 : 155));
        } else {
            int outline = hovered
                    ? (danger ? ColorUtil.withAlpha(DANGER, 190) : ColorUtil.withAlpha(VIOLET, 180))
                    : (danger ? ColorUtil.withAlpha(DANGER, 86) : ColorUtil.withAlpha(GREEN, 62));
            glass(context, x, y, width, height, 12, outline);
            if (hovered) {
                UiRenderer.horizontalGradient(context, x + 16, y + height - 2, width - 32, 2,
                        ColorUtil.withAlpha(danger ? DANGER : GREEN, 175),
                        ColorUtil.withAlpha(danger ? DANGER : VIOLET, 150));
            }
        }
        double labelSize = Math.clamp(height * 0.26, 8.5, 12.5);
        double hintSize = Math.clamp(height * 0.16, 6.2, 8.2);
        double hintWidth = hint == null || hint.isBlank() ? 0
                : UiRenderer.spacedFontWidth(FontFace.GOLOS, hint, hintSize, 0.6);
        double labelRoom = width - 36 - (hintWidth > 0 ? hintWidth + 14 : 0);
        FontFace labelFace = FontFace.GOLOS_SEMI;
        double fittedSize = fitFontSize(labelFace, label, Math.max(18, labelRoom), labelSize, 7.6);
        String fittedLabel = fitFont(labelFace, label, Math.max(18, labelRoom), fittedSize);
        UiRenderer.font(context, labelFace,
                fittedLabel, x + 18, y + (height - fittedSize * 1.2) / 2.0,
                fittedSize, textColor);
        if (hint != null && !hint.isBlank()) {
            double hintX = x + width - 16 - hintWidth;
            UiRenderer.spacedFont(context, FontFace.GOLOS, hint, hintX,
                    y + (height - hintSize * 1.2) / 2.0,
                    hintSize, 0.6, primary ? 0xB40A140F : MUTED);
        }
    }

    public static void pill(DrawContext context, String text, double x, double y, int color) {
        double width = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, 8.2) + 22;
        UiRenderer.roundedRect(context, x, y, width, 20, 10, ColorUtil.withAlpha(color, 26));
        UiRenderer.roundedBorder(context, x, y, width, 20, 10, 1, ColorUtil.withAlpha(color, 95));
        UiRenderer.roundedRect(context, x + 7, y + 8, 4, 4, 2, color);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, text, x + 15, y + 5.1, 8.2, color);
    }

    private static Identifier id(String path) {
        return Identifier.of(LunacyVisuals.MOD_ID, path);
    }

    private static String fit(String value, double maxWidth, double scale) {
        if (UiRenderer.textWidth(value) * scale <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            if (UiRenderer.textWidth(candidate) * scale <= maxWidth) return candidate;
        }
        return suffix;
    }

    private static double fitFontSize(FontFace face, String value, double maxWidth,
                                      double preferred, double minimum) {
        double size = preferred;
        while (size > minimum && UiRenderer.fontWidth(face, value, size) > maxWidth) {
            size -= 0.2;
        }
        return Math.max(minimum, size);
    }

    private static String fitFont(FontFace face, String value, double maxWidth, double size) {
        if (UiRenderer.fontWidth(face, value, size) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            if (UiRenderer.fontWidth(face, candidate, size) <= maxWidth) return candidate;
        }
        return suffix;
    }

    public record Viewport(double scale, double offsetX, double offsetY,
                           double designWidth, double designHeight) {
        public void begin(DrawContext context) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) offsetX, (float) offsetY);
            context.getMatrices().scale((float) scale, (float) scale);
        }

        public void end(DrawContext context) {
            context.getMatrices().popMatrix();
        }

        public double mouseX(double screenX) { return (screenX - offsetX) / Math.max(0.0001, scale); }
        public double mouseY(double screenY) { return (screenY - offsetY) / Math.max(0.0001, scale); }
    }
}
