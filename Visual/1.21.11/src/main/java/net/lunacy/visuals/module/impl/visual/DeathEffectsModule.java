package net.lunacy.visuals.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.LineRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pure code visual effects on entity death: Lightning bolt, Soul burst, and Neon shatter.
 */
public final class DeathEffectsModule extends Module {
    public enum EffectType {
        LIGHTNING,
        SOUL_BURST,
        NEON_SHATTER
    }

    private static final List<DeathAnim> ANIMATIONS = new CopyOnWriteArrayList<>();

    private final EnumSetting<EffectType> effect = add(new EnumSetting<>(
            "effect", "Death Effect", "Visual animation triggered upon kill", EffectType.LIGHTNING, EffectType.class));

    public DeathEffectsModule() {
        super("death_effects", "Death Effects", "Stylized custom visual animations when killing entities",
                ModuleCategory.RENDER_FX);
        setEnabled(true);
    }

    @Override public String displayValue() { return effect.get().name(); }

    public static void trigger(Vec3d pos) {
        ANIMATIONS.add(new DeathAnim(pos, System.currentTimeMillis()));
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || ANIMATIONS.isEmpty() || context.matrices() == null || context.consumers() == null) {
            return;
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            MatrixStack matrices = context.matrices();
            long now = System.currentTimeMillis();

            VertexConsumer buffer = context.consumers().getBuffer(RenderLayers.linesTranslucent());

            Iterator<DeathAnim> it = ANIMATIONS.iterator();
            while (it.hasNext()) {
                DeathAnim anim = it.next();
                double age = (now - anim.createdAt) / 1000.0;
                if (age >= 2.2) {
                    ANIMATIONS.remove(anim);
                    continue;
                }

                double x = anim.pos.x - camera.x;
                double y = anim.pos.y - camera.y;
                double z = anim.pos.z - camera.z;

                matrices.push();
                matrices.translate(x, y, z);
                MatrixStack.Entry entry = matrices.peek();

                float progress = (float) (age / 2.2);
                int alpha = (int) Math.clamp((1.0f - progress) * 255, 0, 255);

                EffectType type = effect.get();
                if (type == EffectType.LIGHTNING) {
                    renderLightning(entry, buffer, progress, alpha);
                } else if (type == EffectType.SOUL_BURST) {
                    renderSoulBurst(entry, buffer, progress, alpha);
                } else {
                    renderShatter(entry, buffer, progress, alpha);
                }

                matrices.pop();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void renderLightning(MatrixStack.Entry entry, VertexConsumer buffer, float progress, int alpha) {
        float h = 18.0f;
        int color = ColorUtil.withAlpha(LunacyMenuChrome.GREEN, alpha);
        float r = (float) ((color >> 16) & 0xFF) / 255.0f;
        float g = (float) ((color >> 8) & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        float a = alpha / 255.0f;

        float ringRadius = progress * 2.8f;
        for (int i = 0; i < 40; i++) {
            double a1 = i * Math.PI * 2.0 / 40.0;
            double a2 = (i + 1) * Math.PI * 2.0 / 40.0;
            LineRenderer.line(entry, buffer,
                    (float) Math.cos(a1) * ringRadius, 0.05f, (float) Math.sin(a1) * ringRadius,
                    (float) Math.cos(a2) * ringRadius, 0.05f, (float) Math.sin(a2) * ringRadius,
                    r, g, b, a, 2.0f);
        }

        float currentY = 0;
        float currentX = 0;
        float currentZ = 0;
        for (int step = 0; step < 10; step++) {
            float nextY = currentY + (h / 10.0f);
            float nextX = ((step % 2 == 0) ? 0.35f : -0.35f) * (1.0f - progress * 0.5f);
            float nextZ = ((step % 3 == 0) ? 0.25f : -0.25f) * (1.0f - progress * 0.5f);
            LineRenderer.line(entry, buffer,
                    currentX, currentY, currentZ,
                    nextX, nextY, nextZ,
                    r, g, b, a, 2.5f);
            currentX = nextX;
            currentY = nextY;
            currentZ = nextZ;
        }
    }

    private static void renderSoulBurst(MatrixStack.Entry entry, VertexConsumer buffer, float progress, int alpha) {
        float r = 0.65f, g = 0.45f, b = 1.0f, a = alpha / 255.0f;
        for (int ring = 0; ring < 3; ring++) {
            float ringY = progress * 3.5f + ring * 0.6f;
            float radius = (float) Math.sin((progress + ring * 0.2f) * Math.PI) * 1.2f;
            if (radius <= 0) continue;
            for (int i = 0; i < 28; i++) {
                double a1 = i * Math.PI * 2.0 / 28.0;
                double a2 = (i + 1) * Math.PI * 2.0 / 28.0;
                LineRenderer.line(entry, buffer,
                        (float) Math.cos(a1) * radius, ringY, (float) Math.sin(a1) * radius,
                        (float) Math.cos(a2) * radius, ringY, (float) Math.sin(a2) * radius,
                        r, g, b, a, 2.0f);
            }
        }
    }

    private static void renderShatter(MatrixStack.Entry entry, VertexConsumer buffer, float progress, int alpha) {
        float r = 0.35f, g = 0.95f, b = 0.75f, a = alpha / 255.0f;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2.0 / 24.0;
            float dist = progress * 3.0f;
            float py = (float) (progress * 2.2f - progress * progress * 3.5f);
            float px = (float) Math.cos(angle) * dist;
            float pz = (float) Math.sin(angle) * dist;
            LineRenderer.line(entry, buffer,
                    px, py + 0.5f, pz,
                    px * 1.15f, py + 0.75f, pz * 1.15f,
                    r, g, b, a, 2.0f);
        }
    }

    private record DeathAnim(Vec3d pos, long createdAt) {}
}
