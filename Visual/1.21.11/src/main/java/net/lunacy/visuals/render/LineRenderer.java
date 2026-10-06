package net.lunacy.visuals.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Utility for drawing lines adhering to Minecraft 1.21.11's VertexFormats.LINES requirement
 * (Position, Color, Normal, LineWidth).
 */
public final class LineRenderer {
    private LineRenderer() {}

    public static void line(MatrixStack.Entry entry, VertexConsumer lines,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float r, float g, float b, float a,
                            float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-5f) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }

        lines.vertex(entry, x1, y1, z1)
                .color(r, g, b, a)
                .normal(entry, dx, dy, dz)
                .lineWidth(width);

        lines.vertex(entry, x2, y2, z2)
                .color(r, g, b, a)
                .normal(entry, dx, dy, dz)
                .lineWidth(width);
    }
}