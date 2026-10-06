package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.MouseEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class JumpHitParticlesModule extends Module {
    public enum Style { STAR, NEON, SPARK }
    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Crit stars, end-rod neon or electric sparks", Style.STAR, Style.class));
    private final DoubleSetting amount = add(new DoubleSetting(
            "amount", "Amount", "Base particles per event", 12, 4, 24, 1));
    private boolean grounded = true;

    public JumpHitParticlesModule() {
        super("jump_hit_particles", "Jump & Hit Particles", "Responsive jump ring and click-accurate hit burst",
                ModuleCategory.RENDER_FX);
    }

    @Override public String displayValue() { return style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.world == null) { grounded = true; return; }
        boolean nowGrounded = client.player.isOnGround();
        if (grounded && !nowGrounded && client.player.getVelocity().y > 0) {
            int count = VisualQuality.particles(amount.get().intValue());
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count;
                client.particleManager.addParticle(particle(), client.player.getX() + Math.cos(angle) * 0.35,
                        client.player.getY() + 0.05, client.player.getZ() + Math.sin(angle) * 0.35,
                        Math.cos(angle) * 0.015, 0.01, Math.sin(angle) * 0.015);
            }
        }
        grounded = nowGrounded;
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        if (event.type() != MouseEvent.Type.BUTTON || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.action() != GLFW.GLFW_PRESS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || client.world == null
                || !(client.crosshairTarget instanceof EntityHitResult hit)) return;
        int count = VisualQuality.particles(amount.get().intValue());
        for (int i = 0; i < count; i++) {
            double vx = (client.player.getRandom().nextDouble() - 0.5) * 0.15;
            double vy = client.player.getRandom().nextDouble() * 0.1;
            double vz = (client.player.getRandom().nextDouble() - 0.5) * 0.15;
            client.particleManager.addParticle(particle(), hit.getEntity().getX(), hit.getEntity().getBodyY(0.5),
                    hit.getEntity().getZ(), vx, vy, vz);
        }
    }

    private ParticleEffect particle() {
        return switch (style.get()) {
            case STAR -> ParticleTypes.CRIT;
            case NEON -> ParticleTypes.END_ROD;
            case SPARK -> ParticleTypes.ELECTRIC_SPARK;
        };
    }
}
