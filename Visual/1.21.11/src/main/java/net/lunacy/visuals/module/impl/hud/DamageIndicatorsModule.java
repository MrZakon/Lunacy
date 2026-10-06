package net.lunacy.visuals.module.impl.hud;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Modern floating damage numbers in 3D world space with critical hit styling.
 */
public final class DamageIndicatorsModule extends Module {
    private final List<DamagePopup> popups = new CopyOnWriteArrayList<>();
    private final Map<Integer, Float> lastHealth = new ConcurrentHashMap<>();

    private final ColorSetting normalColor = add(new ColorSetting(
            "normal_color", "Normal Color", "Color of regular damage numbers",
            new ColorValue(0xFFFFF050, 0xFFFF7B30, false, 0.5)));
    private final ColorSetting critColor = add(new ColorSetting(
            "crit_color", "Crit Color", "Color of critical damage numbers",
            new ColorValue(0xFFFF3050, 0xFFBC20FF, false, 0.5)));
    private final BooleanSetting showCrits = add(new BooleanSetting(
            "show_crits", "Show Crits", "Highlight critical hits with exclamation icon", true));

    public DamageIndicatorsModule() {
        super("damage_indicators", "Damage Indicators", "Floating animated damage numbers above hit entities",
                ModuleCategory.HUD);
        setEnabled(true);
    }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            lastHealth.clear();
            popups.clear();
            return;
        }

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player) continue;

            float current = living.getHealth();
            Float previous = lastHealth.put(entity.getId(), current);

            if (previous != null && current < previous) {
                float damage = previous - current;
                if (damage > 0.05f) {
                    boolean crit = damage >= 3.5f || (client.player != null && client.player.fallDistance > 0);
                    Vec3d pos = new Vec3d(
                            entity.getX() + (Math.random() - 0.5) * 0.35,
                            entity.getY() + entity.getHeight() * 0.65 + Math.random() * 0.25,
                            entity.getZ() + (Math.random() - 0.5) * 0.35);
                    popups.add(new DamagePopup(pos, damage, crit, System.currentTimeMillis()));
                }
            }
        }
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || popups.isEmpty() || context.matrices() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = context.matrices();
        long now = System.currentTimeMillis();

        Quaternionf cameraRot = client.gameRenderer.getCamera().getRotation();

        Iterator<DamagePopup> it = popups.iterator();
        while (it.hasNext()) {
            DamagePopup popup = it.next();
            double age = (now - popup.createdAt) / 1000.0;
            if (age >= 0.95) {
                popups.remove(popup);
                continue;
            }

            double rise = Math.sin(age / 0.95 * Math.PI * 0.5) * 0.65;
            double x = popup.pos.x - camera.x;
            double y = popup.pos.y + rise - camera.y;
            double z = popup.pos.z - camera.z;

            float alpha = (float) Math.clamp(1.0 - (age / 0.95), 0.0, 1.0);
            int a = (int) (alpha * 255);
            if (a <= 0) continue;

            String text = (popup.crit && showCrits.get() ? "CRIT " : "-") + String.format("%.1f", popup.damage);
            int baseColor = popup.crit ? critColor.get().colorAt(0.5, now) : normalColor.get().colorAt(0.5, now);
            int textColor = ColorUtil.withAlpha(baseColor, a);

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(cameraRot);
            matrices.scale(-0.025f, -0.025f, 0.025f);

            int textWidth = client.textRenderer.getWidth(text);
            client.textRenderer.draw(text, -textWidth / 2.0f, 0, textColor, true,
                    matrices.peek().getPositionMatrix(), context.consumers(),
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);

            matrices.pop();
        }
    }

    private record DamagePopup(Vec3d pos, float damage, boolean crit, long createdAt) {}
}