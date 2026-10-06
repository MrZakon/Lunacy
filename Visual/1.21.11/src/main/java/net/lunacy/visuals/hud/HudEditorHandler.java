package net.lunacy.visuals.hud;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.localization.LunacyI18n;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.impl.hud.HudModule;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.Map;

/**
 * Handles interactive HUD positioning and media playback buttons when ChatScreen is open.
 * NOTE: WatermarkModule is explicitly excluded from moving ("кроме ватемарки").
 */
public final class HudEditorHandler {
    private static HudModule draggedHud = null;
    private static double dragOffsetX = 0;
    private static double dragOffsetY = 0;
    private static double snapGuideX = -1;
    private static double snapGuideY = -1;

    private HudEditorHandler() {}

    public static boolean isChatOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ChatScreen;
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !(client.currentScreen instanceof ChatScreen)) return;

        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // 1. Update dragged HUD module position with smooth magnetic snapping
        if (draggedHud != null) {
            HudBounds bounds = runtime.hudLayout().bounds().get(draggedHud.id());
            if (bounds != null) {
                double targetX = mouseX - dragOffsetX;
                double targetY = mouseY - dragOffsetY;

                // 2px grid snap
                double snapX = Math.round(targetX / 2.0) * 2.0;
                double snapY = Math.round(targetY / 2.0) * 2.0;

                snapGuideX = -1;
                snapGuideY = -1;

                // Magnetic center X snap
                double centerX = screenW / 2.0;
                if (Math.abs(snapX + bounds.width() / 2.0 - centerX) < 6) {
                    snapX = centerX - bounds.width() / 2.0;
                    snapGuideX = centerX;
                }

                // Screen margin X snap (6px)
                if (Math.abs(snapX - 6) < 6) snapX = 6;
                if (Math.abs(snapX + bounds.width() - (screenW - 6)) < 6) snapX = screenW - 6 - bounds.width();

                // Magnetic center Y snap
                double centerY = screenH / 2.0;
                if (Math.abs(snapY + bounds.height() / 2.0 - centerY) < 6) {
                    snapY = centerY - bounds.height() / 2.0;
                    snapGuideY = centerY;
                }

                // Screen margin Y snap (6px)
                if (Math.abs(snapY - 6) < 6) snapY = 6;
                if (Math.abs(snapY + bounds.height() - (screenH - 6)) < 6) snapY = screenH - 6 - bounds.height();

                draggedHud.moveTo(snapX, snapY);
            }
        } else {
            snapGuideX = -1;
            snapGuideY = -1;
        }

        // 2. Draw magnetic guide lines if snapping
        if (snapGuideX >= 0) {
            UiRenderer.rect(context, snapGuideX - 0.5, 0, 1, screenH, 0xAA5CE1E6);
        }
        if (snapGuideY >= 0) {
            UiRenderer.rect(context, 0, snapGuideY - 0.5, screenW, 1, 0xAA5CE1E6);
        }

        // 3. Render badges & outlines for all active HUD elements EXCEPT watermark
        for (Map.Entry<String, HudBounds> entry : runtime.hudLayout().bounds().entrySet()) {
            String id = entry.getKey();
            // Watermark is STRICTLY fixed and non-draggable
            if ("watermark".equals(id)) continue;

            Module module = runtime.modules().find(id).orElse(null);
            if (!(module instanceof HudModule hud) || !hud.isEnabled() || hud.isLocked()) continue;

            HudBounds bounds = entry.getValue();
            boolean hovered = bounds.contains(mouseX, mouseY);
            boolean dragging = (draggedHud == hud);

            int borderColor = dragging ? 0xFF5CE1E6 : hovered ? 0xDD5CE1E6 : 0x50FFFFFF;
            int fillColor = dragging ? 0x225CE1E6 : hovered ? 0x14FFFFFF : 0x00000000;

            // Box outline
            UiRenderer.roundedRect(context, bounds.x() - 2, bounds.y() - 2, bounds.width() + 4, bounds.height() + 4, 6, fillColor);
            UiRenderer.roundedBorder(context, bounds.x() - 2, bounds.y() - 2, bounds.width() + 4, bounds.height() + 4, 6, 1.0, borderColor);

            // Name badge
            String title = LunacyI18n.moduleName(hud);
            double fontW = UiRenderer.fontWidth(FontFace.ROUND, title, 7.5);
            double badgeW = fontW + 14;
            double badgeY = bounds.y() >= 16 ? bounds.y() - 14 : bounds.y() + bounds.height() + 3;

            UiRenderer.roundedRect(context, bounds.x(), badgeY, badgeW, 12, 4, 0xDD0D1410);
            UiRenderer.roundedBorder(context, bounds.x(), badgeY, badgeW, 12, 4, 1.0, borderColor);
            UiRenderer.font(context, FontFace.ROUND, title, bounds.x() + 7, badgeY + 2.5, 7.5, dragging ? 0xFF5CE1E6 : 0xFFEAEAEA);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return false;

        // C. Check if clicking any draggable HUD module (WATERMARK IS STRICTLY EXCLUDED)
        for (Map.Entry<String, HudBounds> entry : runtime.hudLayout().bounds().entrySet()) {
            String id = entry.getKey();
            if ("watermark".equals(id)) continue;

            HudBounds bounds = entry.getValue();
            if (bounds.contains(mouseX, mouseY)) {
                Module module = runtime.modules().find(id).orElse(null);
                if (module instanceof HudModule hud && hud.isEnabled() && !hud.isLocked()) {
                    draggedHud = hud;
                    dragOffsetX = mouseX - bounds.x();
                    dragOffsetY = mouseY - bounds.y();
                    return true;
                }
            }
        }

        return false;
    }

    public static void onMouseRelease() {
        if (draggedHud != null) {
            draggedHud = null;
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime != null && runtime.config() != null) {
                runtime.config().markDirty();
                runtime.config().flush();
            }
        }
    }
}
