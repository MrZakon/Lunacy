package net.lunacy.visuals.mixin;

import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рисуется после Mojang-pass, сохраняя его корректную reload/fade state-machine. */
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
    private static final Identifier LUNACY_LOGO = Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "textures/gui/lv_logo.png");
    @Shadow private float currentProgress;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lunacyvisuals$renderSplash(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                                            CallbackInfo callback) {
        int width = context.guiWidth();
        int height = context.guiHeight();
        context.fillGradient(0, 0, width, height, 0xFF090711, 0xFF151021);
        int logo = Math.min(126, Math.min(width, height) / 3);
        UiRenderer.texture(context, LUNACY_LOGO, width / 2.0 - logo / 2.0,
                height / 2.0 - logo / 2.0, logo, logo, 128, 128);
        renderLoadingBar(context, width, height, logo);
        // Текст здесь не рисуем: GUI_TEXT pipeline ещё может собираться этим же reload.
    }

    private void renderLoadingBar(GuiGraphicsExtractor context, int screenWidth, int screenHeight, int logoSize) {
        double barWidth = Math.min(420, Math.max(160, screenWidth * 0.38));
        double barHeight = 10;
        double x = (screenWidth - barWidth) / 2.0;
        double y = Math.min(screenHeight - 28,
                screenHeight / 2.0 + logoSize / 2.0 + 30);

        UiRenderer.shadow(context, x, y, barWidth, barHeight, barHeight / 2.0,
                ColorUtil.withAlpha(0xFF5CE1E6, 48));
        UiRenderer.roundedRect(context, x, y, barWidth, barHeight, barHeight / 2.0,
                0xE60A0E19);
        UiRenderer.roundedBorder(context, x, y, barWidth, barHeight, barHeight / 2.0, 1,
                0x665CE1E6);

        double inset = 3;
        double innerHeight = barHeight - inset * 2;
        double available = barWidth - inset * 2;
        double fillWidth = available * Math.clamp(currentProgress, 0.0F, 1.0F);
        if (fillWidth < 0.5) return;

        double fillRadius = Math.min(innerHeight / 2.0, fillWidth / 2.0);
        UiRenderer.gradientRoundedRect(context, x + inset, y + inset,
                fillWidth, innerHeight, fillRadius, 0xFF5CE1E6, 0xFFB18CFF);
        if (fillWidth > 4) {
            UiRenderer.roundedRect(context, x + inset + 2, y + inset + 0.5,
                    fillWidth - 4, 1, 0.5, 0x66FFFFFF);
        }
    }
}
