package net.lunacy.visuals.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.LineRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Shader-free selected-block glow and neon chunk borders.
 */
public final class ItemGlintOverlayModule extends Module {
    public enum Overlay { FILL_OUTLINE, FILL, OUTLINE }

    private final ColorSetting glint = add(new ColorSetting(
            "glint", "Glow color", "Animated selected-block color",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.55)));
    private final EnumSetting<Overlay> overlay = add(new EnumSetting<>(
            "overlay", "Block glow", "Fill, outline, or both", Overlay.FILL_OUTLINE, Overlay.class));
    private final DoubleSetting speed = add(new DoubleSetting(
            "speed", "Speed", "Color and opacity animation speed", 0.8, 0.1, 3, 0.1));
    private final DoubleSetting fillAlpha = add(new DoubleSetting(
            "fill_alpha", "Fill opacity", "Opacity of the block surfaces", 0.18, 0.04, 0.55, 0.01));
    private final DoubleSetting lineWidth = add(new DoubleSetting(
            "line_width", "Outline width", "Width of the block shape outline", 2.2, 1, 6, 0.1));
    private final BooleanSetting blockAura = add(new BooleanSetting(
            "block_aura", "Selected block glow", "Color the actual block under the crosshair", true));
    private final BooleanSetting chunkBorders = add(new BooleanSetting(
            "chunk_borders", "Chunk Borders", "Display stylish neon chunk boundaries", false));

    public ItemGlintOverlayModule() {
        super("item_glint_overlay", "Block & Chunk Outlines", "Colored voxel block glow and neon chunk borders",
                ModuleCategory.RENDER_FX);
    }

    @Override public String displayValue() { return overlay.get().name(); }

    public boolean renderBlockOverlay(WorldRenderContext context, OutlineRenderState outlineState) {
        if (!blockAura.get() || context.matrices() == null || context.consumers() == null
                || outlineState == null || outlineState.shape() == null || outlineState.shape().isEmpty()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        double x = outlineState.pos().getX() - camera.x;
        double y = outlineState.pos().getY() - camera.y;
        double z = outlineState.pos().getZ() - camera.z;
        long now = System.currentTimeMillis();
        double phase = CosmeticParticleUtil.wrap(now / (3400.0 / speed.get()));
        double pulse = 0.82 + Math.sin(phase * Math.PI * 2.0) * 0.18;
        int color = glint.get().colorAt(phase, now);

        if (overlay.get() != Overlay.OUTLINE) {
            VertexConsumer fill = context.consumers().getBuffer(RenderLayers.debugFilledBox());
            int fillColor = ColorUtil.withAlpha(color,
                    (int) Math.round(fillAlpha.get() * 255.0 * pulse));
            MatrixStack.Entry entry = context.matrices().peek();
            outlineState.shape().forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    fillBox(fill, entry,
                            x + minX - 0.0015, y + minY - 0.0015, z + minZ - 0.0015,
                            x + maxX + 0.0015, y + maxY + 0.0015, z + maxZ + 0.0015,
                            fillColor));
        }

        if (overlay.get() != Overlay.FILL) {
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
            VertexRendering.drawOutline(context.matrices(), lines, outlineState.shape(), x, y, z,
                    ColorUtil.withAlpha(color, 235), lineWidth.get().floatValue());
        }
        return true;
    }

    public void renderChunkBorders(WorldRenderContext context) {
        if (!chunkBorders.get() || context.matrices() == null || context.consumers() == null) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            BlockPos playerPos = client.player.getBlockPos();
            int chunkX = (playerPos.getX() >> 4) << 4;
            int chunkZ = (playerPos.getZ() >> 4) << 4;

            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            MatrixStack.Entry entry = matrices.peek();

            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
            int color = glint.get().colorAt(0.5, System.currentTimeMillis());
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            float minY = (float) Math.max(-64, playerPos.getY() - 16);
            float maxY = (float) Math.min(320, playerPos.getY() + 32);

            drawColumn(entry, lines, chunkX, chunkZ, minY, maxY, r, g, b);
            drawColumn(entry, lines, chunkX + 16, chunkZ, minY, maxY, r, g, b);
            drawColumn(entry, lines, chunkX, chunkZ + 16, minY, maxY, r, g, b);
            drawColumn(entry, lines, chunkX + 16, chunkZ + 16, minY, maxY, r, g, b);

            matrices.pop();
        } catch (Throwable ignored) {
        }
    }

    private static void drawColumn(MatrixStack.Entry entry, VertexConsumer lines, float x, float z, float y1, float y2,
                                   float r, float g, float b) {
        LineRenderer.line(entry, lines, x, y1, z, x, y2, z, r, g, b, 0.95f, 2.0f);
    }

    private static void fillBox(VertexConsumer consumer, MatrixStack.Entry matrix,
                                double x1, double y1, double z1, double x2, double y2, double z2,
                                int color) {
        quad(consumer, matrix, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(consumer, matrix, color, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(consumer, matrix, color, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
        quad(consumer, matrix, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        quad(consumer, matrix, color, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(consumer, matrix, color, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry matrix, int color,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz) {
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(color);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(color);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(color);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(color);
    }
}