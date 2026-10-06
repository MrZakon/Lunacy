package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustColorTransitionOptions;

/** Shared colored-particle primitives for local cosmetics. */
final class CosmeticParticleUtil {
    private CosmeticParticleUtil() {}

    static void dust(Minecraft client, ColorSetting colors, double phase, float size,
                     double x, double y, double z) {
        long now = System.currentTimeMillis();
        int from = colors.get().colorAt(wrap(phase), now) & 0x00FF_FFFF;
        int to = colors.get().colorAt(wrap(phase + 0.22), now) & 0x00FF_FFFF;
        client.particleEngine.createParticle(new DustColorTransitionOptions(from, to, size),
                x, y, z, 0, 0, 0);
    }

    static Point local(double originX, double originY, double originZ, double yaw,
                       double right, double up, double forward) {
        double x = originX + Math.cos(yaw) * right - Math.sin(yaw) * forward;
        double z = originZ + Math.sin(yaw) * right + Math.cos(yaw) * forward;
        return new Point(x, originY + up, z);
    }

    static double wrap(double value) {
        double wrapped = value % 1.0;
        return wrapped < 0 ? wrapped + 1.0 : wrapped;
    }

    record Point(double x, double y, double z) {}
}
