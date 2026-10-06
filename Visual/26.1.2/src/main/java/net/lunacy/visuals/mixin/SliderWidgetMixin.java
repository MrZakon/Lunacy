package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives vanilla option sliders the same rounded plate while preserving native value handling. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderWidgetMixin {
    @Shadow protected double value;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void lunacy$renderSlider(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!LunacyVanillaChrome.shouldTheme(client.screen)) return;

        AbstractWidget widget = (AbstractWidget) (Object) this;
        double x = widget.getX();
        double y = widget.getY();
        double width = widget.getWidth();
        double height = widget.getHeight();
        double radius = Math.min(12, Math.max(5, height / 2.0));
        boolean hovered = widget.isHovered() || widget.isFocused();

        UiRenderer.roundedRect(context, x, y, width, height, radius,
                hovered ? ColorUtil.withAlpha(LunacyMenuChrome.VIOLET, 180)
                        : ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 70));
        UiRenderer.roundedRect(context, x + 1, y + 1,
                width - 2, height - 2, Math.max(2, radius - 1),
                hovered ? 0xF0202D27 : 0xEC151F1A);
        double trackX = x + 7;
        double trackY = y + height - 5;
        double trackWidth = Math.max(1, width - 14);
        UiRenderer.roundedRect(context, trackX, trackY, trackWidth, 2, 1, 0x5A6B7A72);
        UiRenderer.gradientRoundedRect(context, trackX, trackY,
                trackWidth * Math.clamp(value, 0.0, 1.0), 2, 1,
                LunacyMenuChrome.GREEN, LunacyMenuChrome.VIOLET);
        double handleX = trackX + trackWidth * Math.clamp(value, 0.0, 1.0);
        UiRenderer.roundedRect(context, handleX - 3, trackY - 2, 6, 6, 3,
                hovered ? LunacyMenuChrome.VIOLET : LunacyMenuChrome.GREEN);
        String raw = widget.getMessage().getString();
        double fontSize = Math.clamp(height * 0.42, 7.4, 9.2);
        String label = fit(raw, Math.max(8, width - 18), fontSize);
        double labelWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, fontSize);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, label,
                x + (width - labelWidth) / 2.0,
                y + (height - fontSize * 1.2) / 2.0 - 1.0,
                fontSize, widget.active ? 0xFFF1F4F2 : 0xFF68746E);
        ci.cancel();
    }

    private static String fit(String value, double maxWidth, double size) {
        if (UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size) <= maxWidth) return value;
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + "…";
            if (UiRenderer.fontWidth(FontFace.GOLOS_SEMI, candidate, size) <= maxWidth) return candidate;
        }
        return "…";
    }
}
