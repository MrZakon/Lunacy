package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.core.particles.ParticleTypes;

public final class MotionTrailsModule extends Module {
    private final DoubleSetting density = add(new DoubleSetting(
            "density", "Density", "Particles emitted per trail step", 3, 1, 8, 1));
    private final DoubleSetting lifetimeSpacing = add(new DoubleSetting(
            "spacing", "Trail width", "Width behind the player's movement", 0.28, 0.05, 0.8, 0.05));
    private int tick;

    public MotionTrailsModule() {
        super("motion_trails", "MotionTrails", "Direction-aware movement trail with a particle budget",
                ModuleCategory.COSMETICS);
    }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.level == null) { tick = 0; return; }
        var velocity = client.player.getDeltaMovement();
        double horizontal = velocity.horizontalDistance();
        if (horizontal < 0.045 || ++tick % VisualQuality.cadence(2) != 0) return;
        int count = VisualQuality.particles(density.get().intValue());
        double nx = -velocity.x / horizontal;
        double nz = -velocity.z / horizontal;
        for (int i = 0; i < count; i++) {
            double side = (i - (count - 1) * 0.5) * lifetimeSpacing.get() / Math.max(1, count - 1);
            client.particleEngine.createParticle(ParticleTypes.END_ROD,
                    client.player.getX() + nx * 0.32 - nz * side,
                    client.player.getY() + 0.08 + i * 0.025,
                    client.player.getZ() + nz * 0.32 + nx * side, 0, 0.003, 0);
        }
    }

    @Override protected void onDisable() { tick = 0; }
}
