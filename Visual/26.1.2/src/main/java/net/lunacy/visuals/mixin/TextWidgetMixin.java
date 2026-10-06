package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the bundled Golos face for native one-line menu titles and captions. */
@Mixin(AbstractStringWidget.class)
public abstract class TextWidgetMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void lunacy$renderCleanText(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                        float delta, CallbackInfo ci) {
        if (!LunacyVanillaChrome.shouldTheme(Minecraft.getInstance().screen)) return;
        if (!((Object) this instanceof StringWidget widget)) return;

        String value = widget.getMessage().getString();
        if (value == null || value.isBlank()) {
            ci.cancel();
            return;
        }
        double size = 9.0;
        double available = Math.max(1, widget.getWidth());
        double natural = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size);
        if (natural > available) size = Math.max(7.2, size * available / natural);
        double textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size);
        double x = widget.getX() + (widget.getWidth() - textWidth) / 2.0;
        double y = widget.getY() + (widget.getHeight() - size * 1.2) / 2.0;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, value, x, y, size,
                widget.isHovered() ? LunacyMenuChrome.GREEN : LunacyMenuChrome.TEXT);
        ci.cancel();
    }
}
