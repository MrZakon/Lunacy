package net.lunacy.visuals.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.LineRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts and renders the ballistic trajectory and landing marker for Ender Pearls, Bows, Crossbows, and Throwables.
 */
public final class ProjectilePredictionModule extends Module {
    private final BooleanSetting pearl = add(new BooleanSetting("pearl", "Ender Pearl", "Predict ender pearl throws", true));
    private final BooleanSetting bow = add(new BooleanSetting("bow", "Bows", "Predict bow and crossbow arrows", true));
    private final BooleanSetting potions = add(new BooleanSetting("potions", "Potions", "Predict splash potion throws", true));
    private final BooleanSetting other = add(new BooleanSetting("other", "Other", "Predict snowballs, eggs and wind charges", true));
    private final ColorSetting color = add(new ColorSetting("color", "Arc Color", "Color of trajectory arc",
            new ColorValue(0xFF4FFFD8, 0xFF9E5CFF, false, 0.75)));

    public ProjectilePredictionModule() {
        super("projectile_prediction", "Trajectory Prediction", "Ballistic arc prediction for pearls, arrows, and potions",
                ModuleCategory.RENDER_FX);
        setEnabled(true);
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || context.matrices() == null || context.consumers() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        ItemStack stack = player.getMainHandStack();
        if (!isThrowable(stack.getItem())) {
            stack = player.getOffHandStack();
            if (!isThrowable(stack.getItem())) return;
        }

        Item item = stack.getItem();
        if (item instanceof EnderPearlItem && !pearl.get()) return;
        if ((item instanceof BowItem || item instanceof CrossbowItem) && !bow.get()) return;
        if ((item instanceof SplashPotionItem || item instanceof LingeringPotionItem) && !potions.get()) return;

        double speed = 1.5;
        double gravity = 0.03;
        double drag = 0.99;
        float pitchOffset = 0.0f;

        if (item instanceof BowItem) {
            int useTicks = player.getItemUseTime();
            float pull = BowItem.getPullProgress(useTicks);
            if (pull < 0.1f) pull = 1.0f;
            speed = pull * 3.0;
            gravity = 0.05;
        } else if (item instanceof CrossbowItem) {
            speed = 3.15;
            gravity = 0.05;
        } else if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
            speed = 0.5;
            gravity = 0.05;
            pitchOffset = -20.0f;
        }

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        float yaw = player.getYaw();
        float pitch = player.getPitch() + pitchOffset;

        double vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * speed;
        double vy = -Math.sin(Math.toRadians(pitch)) * speed;
        double vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * speed;

        Vec3d currentPos = player.getEyePos();
        Vec3d velocity = new Vec3d(vx, vy, vz).add(player.getVelocity());

        List<Vec3d> path = new ArrayList<>(64);
        path.add(currentPos);

        HitResult hit = null;
        for (int step = 0; step < 100; step++) {
            Vec3d nextPos = currentPos.add(velocity);

            RaycastContext rayCtx = new RaycastContext(
                    currentPos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player);
            BlockHitResult blockHit = client.world.raycast(rayCtx);

            if (blockHit.getType() != HitResult.Type.MISS) {
                hit = blockHit;
                path.add(blockHit.getPos());
                break;
            }

            path.add(nextPos);
            currentPos = nextPos;
            velocity = new Vec3d(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
        }

        try {
            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            MatrixStack.Entry entry = matrices.peek();

            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
            int total = path.size();
            long now = System.currentTimeMillis();

            for (int i = 0; i < total - 1; i++) {
                double t = (double) i / total;
                int c = color.get().colorAt(t, now);
                float r = ((c >> 16) & 0xFF) / 255.0f;
                float g = ((c >> 8) & 0xFF) / 255.0f;
                float b = (c & 0xFF) / 255.0f;

                Vec3d p1 = path.get(i);
                Vec3d p2 = path.get(i + 1);

                LineRenderer.line(entry, lines,
                        (float) p1.x, (float) p1.y, (float) p1.z,
                        (float) p2.x, (float) p2.y, (float) p2.z,
                        r, g, b, 0.95f, 2.5f);
            }

            if (hit instanceof BlockHitResult blockHit) {
                Vec3d hitPos = blockHit.getPos();
                Direction side = blockHit.getSide();
                drawLandingRing(entry, lines, hitPos, side, color.get().colorAt(1.0, now));
            }

            matrices.pop();
        } catch (Throwable ignored) {
        }
    }

    private static void drawLandingRing(MatrixStack.Entry entry, VertexConsumer lines, Vec3d center, Direction side, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float radius = 0.35f;
        int segments = 16;
        Vec3d offset = Vec3d.of(side.getVector()).multiply(0.02);
        Vec3d p = center.add(offset);

        for (int i = 0; i < segments; i++) {
            double a1 = i * Math.PI * 2.0 / segments;
            double a2 = (i + 1) * Math.PI * 2.0 / segments;

            Vec3d v1, v2;
            if (side.getAxis() == Direction.Axis.Y) {
                v1 = p.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
                v2 = p.add(Math.cos(a2) * radius, 0, Math.sin(a2) * radius);
            } else if (side.getAxis() == Direction.Axis.X) {
                v1 = p.add(0, Math.cos(a1) * radius, Math.sin(a1) * radius);
                v2 = p.add(0, Math.cos(a2) * radius, Math.sin(a2) * radius);
            } else {
                v1 = p.add(Math.cos(a1) * radius, Math.sin(a1) * radius, 0);
                v2 = p.add(Math.cos(a2) * radius, Math.sin(a2) * radius, 0);
            }

            LineRenderer.line(entry, lines,
                    (float) v1.x, (float) v1.y, (float) v1.z,
                    (float) v2.x, (float) v2.y, (float) v2.z,
                    r, g, b, 1.0f, 2.0f);
        }
    }

    private boolean isThrowable(Item item) {
        return item instanceof EnderPearlItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof WindChargeItem;
    }
}