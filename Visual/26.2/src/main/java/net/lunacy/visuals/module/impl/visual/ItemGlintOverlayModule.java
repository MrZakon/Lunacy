package net.lunacy.visuals.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.Vec3;

/** Shader-free selected-block glow using the block's real voxel shape. */
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

    public ItemGlintOverlayModule() {
        super("item_glint_overlay", "Block Glow", "Colored voxel-shape fill and outline without particles or shaders",
                ModuleCategory.RENDER_FX);
    }

    @Override public String displayValue() { return overlay.get().name(); }

    /** @return true when the vanilla outline should be replaced. */
    public boolean renderBlockOverlay(PoseStack matrices, SubmitNodeCollector collector,
                                      BlockOutlineRenderState outlineState) {
        if (!blockAura.get() || matrices == null || collector == null
                || outlineState == null || outlineState.shape() == null || outlineState.shape().isEmpty()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        double x = outlineState.pos().getX() - camera.x;
        double y = outlineState.pos().getY() - camera.y;
        double z = outlineState.pos().getZ() - camera.z;
        long now = System.currentTimeMillis();
        double phase = CosmeticParticleUtil.wrap(now / (3400.0 / speed.get()));
        double pulse = 0.82 + Math.sin(phase * Math.PI * 2.0) * 0.18;
        int color = glint.get().colorAt(phase, now);

        if (overlay.get() != Overlay.OUTLINE) {
            int fillColor = ColorUtil.withAlpha(color,
                    (int) Math.round(fillAlpha.get() * 255.0 * pulse));
            collector.submitCustomGeometry(matrices, RenderTypes.debugFilledBox(), (entry, fill) ->
                    outlineState.shape().forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                            fillBox(fill, entry,
                                    x + minX - 0.0015, y + minY - 0.0015, z + minZ - 0.0015,
                                    x + maxX + 0.0015, y + maxY + 0.0015, z + maxZ + 0.0015,
                                    fillColor)));
        }

        if (overlay.get() != Overlay.FILL) {
            matrices.pushPose();
            matrices.translate(x, y, z);
            collector.submitShapeOutline(matrices, outlineState.shape(), RenderTypes.linesTranslucent(),
                    ColorUtil.withAlpha(color, 235), lineWidth.get().floatValue(), true);
            matrices.popPose();
        }
        return true;
    }

    private static void fillBox(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose matrix,
                                double x1, double y1, double z1, double x2, double y2, double z2,
                                int color) {
        quad(consumer, matrix, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(consumer, matrix, color, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(consumer, matrix, color, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
        quad(consumer, matrix, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
        quad(consumer, matrix, color, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(consumer, matrix, color, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose matrix, int color,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz) {
        consumer.addVertex(matrix, (float) ax, (float) ay, (float) az).setColor(color);
        consumer.addVertex(matrix, (float) bx, (float) by, (float) bz).setColor(color);
        consumer.addVertex(matrix, (float) cx, (float) cy, (float) cz).setColor(color);
        consumer.addVertex(matrix, (float) dx, (float) dy, (float) dz).setColor(color);
    }
}
