package net.lunacy.visuals.render;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Быстрые 2D-примитивы для HUD и экранов; координаты уже в GUI-space. */
public final class UiRenderer {
    private UiRenderer() {}

    public static void rect(DrawContext context, double x, double y, double width, double height, int color) {
        context.fill(round(x), round(y), round(x + width), round(y + height), color);
    }

    public static void roundedRect(DrawContext context, double x, double y, double width, double height,
                                   double radius, int color) {
        double r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5));
        if (r <= 1.0) {
            rect(context, x, y, width, height, color);
            return;
        }

        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;

        int ir = (int) Math.ceil(r);
        double midY = y + ir;
        double midH = height - ir * 2.0;
        if (midH > 0) {
            rect(context, x, midY, width, midH, color);
        }

        for (int i = 0; i < ir; i++) {
            double dy = r - (i + 0.5);
            double dx = dy <= 0.0 ? r : (dy >= r ? 0.0 : Math.sqrt(r * r - dy * dy));
            double inset = r - dx;
            double rowW = width - inset * 2.0;
            if (rowW > 0) {
                rect(context, x + inset, y + i, rowW, 1.0, color);
                rect(context, x + inset, y + height - 1.0 - i, rowW, 1.0, color);
            }
        }
    }

    public static void shadow(DrawContext context, double x, double y, double width, double height,
                              double radius, int color) {
        int baseAlpha = (color >> 24) & 0xFF;
        if (baseAlpha <= 0) return;
        int lAlpha = Math.max(2, (int) (baseAlpha * 0.35f));
        roundedRect(context, x - 2.0, y - 1.0, width + 4.0, height + 4.0, radius + 2.0, ColorUtil.withAlpha(color, lAlpha));
    }

    public static void fastRoundedRect(DrawContext context, double x, double y, double width, double height,
                                       double radius, int color) {
        roundedRect(context, x, y, width, height, radius, color);
    }

    public static void border(DrawContext context, double x, double y, double width, double height,
                              double thickness, int color) {
        rect(context, x, y, width, thickness, color);
        rect(context, x, y + height - thickness, width, thickness, color);
        rect(context, x, y, thickness, height, color);
        rect(context, x + width - thickness, y, thickness, height, color);
    }

    /** Draws a continuous pixel-perfect rounded outline without corner gaps or floating dots. */
    public static void roundedBorder(DrawContext context, double x, double y, double width, double height,
                                     double radius, double thickness, int color) {
        double r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5));
        double t = Math.max(0.75, Math.min(thickness, r));
        if (r <= 1.0) {
            border(context, x, y, width, height, t, color);
            return;
        }

        int alpha = (color >> 24) & 0xFF;
        if (alpha <= 0) return;

        int ir = (int) Math.ceil(r);
        double rin = Math.max(0.0, r - t);

        double midY = y + ir;
        double midH = height - ir * 2.0;
        if (midH > 0) {
            rect(context, x, midY, t, midH, color);
            rect(context, x + width - t, midY, t, midH, color);
        }

        for (int i = 0; i < ir; i++) {
            double dy = r - (i + 0.5);
            double dxOut = dy <= 0.0 ? r : (dy >= r ? 0.0 : Math.sqrt(r * r - dy * dy));
            double xOutLeft = x + r - dxOut;
            double xOutRight = x + width - r + dxOut;

            double topY = y + i;
            double botY = y + height - 1.0 - i;

            if (rin > 0.0 && dy < rin) {
                double dxIn = Math.sqrt(rin * rin - dy * dy);
                double xInLeft = x + r - dxIn;
                double xInRight = x + width - r + dxIn;

                double leftW = Math.max(0.0, xInLeft - xOutLeft);
                double rightW = Math.max(0.0, xOutRight - xInRight);

                if (leftW > 0) {
                    rect(context, xOutLeft, topY, leftW, 1.0, color);
                    rect(context, xOutLeft, botY, leftW, 1.0, color);
                }
                if (rightW > 0) {
                    rect(context, xInRight, topY, rightW, 1.0, color);
                    rect(context, xInRight, botY, rightW, 1.0, color);
                }
            } else {
                double spanW = Math.max(0.0, xOutRight - xOutLeft);
                if (spanW > 0) {
                    rect(context, xOutLeft, topY, spanW, 1.0, color);
                    rect(context, xOutLeft, botY, spanW, 1.0, color);
                }
            }
        }
    }

    public static void horizontalGradient(DrawContext context, double x, double y, double width, double height,
                                          int leftColor, int rightColor) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().rotate((float) (-Math.PI / 2.0));
        context.fillGradient(round(-height), 0, 0, round(width), leftColor, rightColor);
        context.getMatrices().popMatrix();
    }

    public static void verticalGradientRoundedRect(DrawContext context, double x, double y,
                                                   double width, double height, double radius,
                                                   int topColor, int bottomColor) {
        int h = Math.max(1, round(height));
        double r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5));
        if (r <= 1.0) {
            context.fillGradient(round(x), round(y), round(x + width), round(y + height),
                    topColor, bottomColor);
            return;
        }

        int ir = (int) Math.ceil(r);
        int middleTop = colorAt(topColor, bottomColor, ir, h);
        int middleBottom = colorAt(topColor, bottomColor, Math.max(0, h - ir), h);

        double midY = y + ir;
        double midH = height - ir * 2.0;
        if (midH > 0) {
            context.fillGradient(round(x), round(midY), round(x + width), round(midY + midH),
                    middleTop, middleBottom);
        }

        for (int i = 0; i < ir; i++) {
            double dy = r - (i + 0.5);
            double dx = dy <= 0.0 ? r : (dy >= r ? 0.0 : Math.sqrt(r * r - dy * dy));
            double inset = r - dx;
            double rowW = width - inset * 2.0;
            if (rowW > 0) {
                int cTop = colorAt(topColor, bottomColor, i, h);
                int cBot = colorAt(topColor, bottomColor, Math.max(0, (int) Math.round(height - 1.0 - i)), h);
                rect(context, x + inset, y + i, rowW, 1.0, cTop);
                rect(context, x + inset, y + height - 1.0 - i, rowW, 1.0, cBot);
            }
        }
    }

    /** A shader-free horizontal gradient with rounded end caps. */
    public static void gradientRoundedRect(DrawContext context, double x, double y, double width, double height,
                                           double radius, int leftColor, int rightColor) {
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0));
        roundedRect(context, x, y, width, height, r, leftColor);
        if (width <= r * 2.0) return;
        horizontalGradient(context, x + r, y, width - r * 2.0, height, leftColor, rightColor);
        roundedRect(context, x + width - r * 2.0, y, r * 2.0, height, r, rightColor);
    }

    public static void text(DrawContext context, String value, double x, double y, int color, boolean shadow) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            context.drawText(MinecraftClient.getInstance().textRenderer, value, round(x), round(y), color, shadow);
            return;
        }
        if (shadow) runtime.renderEngine().fonts().draw(context, value, (float) x + 1, (float) y + 1,
                9.0F, ColorUtil.withAlpha(0xFF000000, Math.max(70, (color >>> 24) / 2)));
        runtime.renderEngine().fonts().draw(context, value, (float) x, (float) y, 9.0F, color);
    }

    public static int textWidth(String value) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? MinecraftClient.getInstance().textRenderer.getWidth(value)
                : Math.round(runtime.renderEngine().fonts().width(value, 9.0F));
    }

    public static void textScaled(DrawContext context, String value, double x, double y, int color,
                                  boolean shadow, double scale) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) x, (float) y);
            context.getMatrices().scale((float) scale, (float) scale);
            context.drawText(MinecraftClient.getInstance().textRenderer, value, 0, 0, color, shadow);
            context.getMatrices().popMatrix();
            return;
        }
        float size = (float) (9.0 * scale);
        if (shadow) runtime.renderEngine().fonts().draw(context, value, (float) x + 1, (float) y + 1,
                size, ColorUtil.withAlpha(0xFF000000, Math.max(70, (color >>> 24) / 2)));
        runtime.renderEngine().fonts().draw(context, value, (float) x, (float) y, size, color);
    }

    public static void font(DrawContext context, FontFace face, String value,
                            double x, double y, double size, int color) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) {
            textScaled(context, value, x, y, color, false, size / 9.0);
            return;
        }
        runtime.renderEngine().fonts().draw(context, face, value, (float) x, (float) y, (float) size, color);
    }

    public static double fontWidth(FontFace face, String value, double size) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? MinecraftClient.getInstance().textRenderer.getWidth(value) * size / 9.0
                : runtime.renderEngine().fonts().width(face, value, (float) size);
    }

    /** Minecraft's batched font path, used by dense screens for maximum clarity and speed. */
    public static void vanillaText(DrawContext context, String value, double x, double y,
                                   double scale, int color, boolean shadow) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) x, (float) y);
        context.getMatrices().scale((float) scale, (float) scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, value, 0, 0, color, shadow);
        context.getMatrices().popMatrix();
    }

    public static double vanillaTextWidth(String value, double scale) {
        return MinecraftClient.getInstance().textRenderer.getWidth(value) * scale;
    }

    public static void spacedFont(DrawContext context, FontFace face, String value,
                                  double x, double y, double size, double spacing, int color) {
        double cursor = x;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            font(context, face, glyph, cursor, y, size, color);
            cursor += fontWidth(face, glyph, size) + spacing;
            offset += Character.charCount(codePoint);
        }
    }

    public static double spacedFontWidth(FontFace face, String value, double size, double spacing) {
        double width = 0;
        int count = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += fontWidth(face, new String(Character.toChars(codePoint)), size);
            count++;
            offset += Character.charCount(codePoint);
        }
        return width + Math.max(0, count - 1) * spacing;
    }

    public static void spacedText(DrawContext context, String value, double x, double y, int color,
                                  double scale, int spacing) {
        double cursor = x;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            textScaled(context, glyph, cursor, y, color, false, scale);
            cursor += (textWidth(glyph) + spacing) * scale;
            offset += Character.charCount(codePoint);
        }
    }

    public static double spacedTextWidth(String value, double scale, int spacing) {
        double width = 0;
        int glyphs = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += textWidth(new String(Character.toChars(codePoint))) * scale;
            glyphs++;
            offset += Character.charCount(codePoint);
        }
        return width + Math.max(0, glyphs - 1) * spacing * scale;
    }

    /** Draws a complete PNG at GUI coordinates without making callers repeat pipeline boilerplate. */
    public static void texture(DrawContext context, Identifier texture, double x, double y,
                               double width, double height, int textureWidth, int textureHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), 0, 0,
                round(width), round(height), textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Draws a complete PNG with an ARGB multiplier, useful for restrained background artwork. */
    public static void texture(DrawContext context, Identifier texture, double x, double y,
                               double width, double height, int textureWidth, int textureHeight, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), 0, 0,
                round(width), round(height), textureWidth, textureHeight, textureWidth, textureHeight, color);
    }

    /** Draws a full texture with a tiny source inset to prevent edge texel repetition when scaled. */
    public static void textureInset(DrawContext context, Identifier texture, double x, double y,
                                    double width, double height, int textureWidth, int textureHeight,
                                    int inset) {
        int safeInset = Math.max(0, Math.min(inset, Math.min(textureWidth, textureHeight) / 4));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y),
                safeInset, safeInset, round(width), round(height),
                Math.max(1, textureWidth - safeInset * 2), Math.max(1, textureHeight - safeInset * 2),
                textureWidth, textureHeight);
    }

    public static void textureRegion(DrawContext context, Identifier texture, double x, double y,
                                     double width, double height, float sourceX, float sourceY,
                                     int sourceWidth, int sourceHeight,
                                     int textureWidth, int textureHeight, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, round(x), round(y), sourceX, sourceY,
                round(width), round(height), sourceWidth, sourceHeight, textureWidth, textureHeight, color);
    }

    /** Draws a Minecraft GUI sprite, including its nine-slice metadata when present. */
    public static void guiSprite(DrawContext context, Identifier sprite, double x, double y,
                                 double width, double height) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, round(x), round(y), round(width), round(height));
    }

    public static boolean inside(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /** Размытие полностью отключено по требованию. */
    public static void tryBlur(DrawContext context) {
        // no-op
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static int roundedInset(int row, int height, int radius) {
        if (radius <= 1 || (row >= radius && row < height - radius)) return 0;
        double dy = row < radius ? radius - row - 0.5 : row - (height - radius) + 0.5;
        return (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
    }

    private static int colorAt(int top, int bottom, int row, int height) {
        return ColorUtil.mix(top, bottom, row / (float) Math.max(1, height - 1));
    }
}
