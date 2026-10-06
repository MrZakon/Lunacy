package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.MouseEvent;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class CustomCrosshairModule extends Module {
    private static final Identifier HIT_TEXTURE = Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "client/xuynya/hit.png");
    public enum Style { CROSS, DOT, CIRCLE, CROSS_DOT }
    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Cross, dot, circle or combined reticle", Style.CROSS_DOT, Style.class));
    private final DoubleSetting gap = add(new DoubleSetting("gap", "Gap", "Center gap", 3, 0, 12, 0.5));
    private final DoubleSetting length = add(new DoubleSetting("length", "Length", "Arm length", 5, 1, 16, 0.5));
    private final DoubleSetting thickness = add(new DoubleSetting("thickness", "Thickness", "Line thickness", 1, 1, 4, 1));
    private final BooleanSetting dynamic = add(new BooleanSetting("dynamic", "Dynamic", "Open with attack cooldown", true));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", "Hide vanilla", "Hide the vanilla crosshair", true));
    private final BooleanSetting hitMarker = add(new BooleanSetting("hit_marker", "Hit marker", "Flash diagonal markers on entity attack", true));
    private final ColorSetting color = add(new ColorSetting("color", "Color", "Reticle color", ColorValue.solid(0xFFF5F2FF)));
    private long hitAt;

    public CustomCrosshairModule() {
        super("custom_crosshair", "Custom Crosshair", "Dynamic reticle with click-accurate hit marker",
                ModuleCategory.HUD);
    }

    @Override public String displayValue() { return style.get().name(); }
    public boolean hidesVanilla() { return hideVanilla.get(); }

    @Subscribe
    private void onRender(Render2DEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != null) return;
        double cx = client.getWindow().getGuiScaledWidth() * 0.5;
        double cy = client.getWindow().getGuiScaledHeight() * 0.5;
        double spread = gap.get();
        if (dynamic.get() && client.player != null) spread += (1.0 - client.player.getAttackStrengthScale(0)) * 4.0;
        double len = length.get();
        double thick = thickness.get();
        int argb = color.get().colorAt(0, System.currentTimeMillis());
        if (style.get() == Style.DOT || style.get() == Style.CROSS_DOT) {
            UiRenderer.rect(event.context(), cx - thick / 2, cy - thick / 2, thick, thick, argb);
        }
        if (style.get() == Style.CIRCLE) {
            double radius = spread + 2;
            for (int i = 0; i < 28; i++) {
                double angle = Math.PI * 2.0 * i / 28.0;
                UiRenderer.rect(event.context(), cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius,
                        Math.max(1, thick), Math.max(1, thick), argb);
            }
        } else if (style.get() != Style.DOT) {
            UiRenderer.rect(event.context(), cx - thick / 2, cy - spread - len, thick, len, argb);
            UiRenderer.rect(event.context(), cx - thick / 2, cy + spread, thick, len, argb);
            UiRenderer.rect(event.context(), cx - spread - len, cy - thick / 2, len, thick, argb);
            UiRenderer.rect(event.context(), cx + spread, cy - thick / 2, len, thick, argb);
        }
        if (hitMarker.get() && System.currentTimeMillis() - hitAt < 170) {
            double age = (System.currentTimeMillis() - hitAt) / 170.0;
            double size = 17 + age * 8;
            int alpha = (int) Math.round((1.0 - age) * 255.0);
            int marker = (Math.clamp(alpha, 0, 255) << 24) | 0x00FF7188;
            UiRenderer.texture(event.context(), HIT_TEXTURE, cx - size * 0.5, cy - size * 0.5,
                    size, size, 124, 124, marker);
        }
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (event.type() == MouseEvent.Type.BUTTON && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.action() == GLFW.GLFW_PRESS && client.gui.screen() == null
                && client.hitResult instanceof EntityHitResult) hitAt = System.currentTimeMillis();
    }
}
