package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square checkbox sprites with a compact rounded check control. */
@Mixin(Checkbox.class)
public abstract class CheckboxWidgetMixin {
    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void lunacy$drawCheck(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        if (!LunacyVanillaChrome.shouldTheme(Minecraft.getInstance().gui.screen())) return;
        Checkbox widget = (Checkbox) (Object) this;
        int size = Math.min(widget.getHeight(), 17);
        int x = widget.getX();
        int y = widget.getY() + Math.max(0, (widget.getHeight() - size) / 2);
        boolean checked = widget.selected();
        boolean hovered = widget.isHovered() || widget.isFocused();
        int accent = checked ? LunacyMenuChrome.GREEN : LunacyMenuChrome.VIOLET;
        UiRenderer.roundedRect(context, x, y, size, size, 5,
                checked ? ColorUtil.withAlpha(accent, 230) : 0xE617211C);
        UiRenderer.roundedBorder(context, x, y, size, size, 5, 1,
                ColorUtil.withAlpha(accent, hovered ? 195 : 85));
        if (checked) {
            UiRenderer.font(context, FontFace.GOLOS_SEMI, "✓", x + 4.2, y + 2.6,
                    10.2, LunacyMenuChrome.INK);
        }
        String label = widget.getMessage().getString();
        if (label != null && !label.isBlank()) {
            double fontSize = 8.6;
            UiRenderer.font(context, FontFace.GOLOS, label, x + size + 6,
                    y + (size - fontSize * 1.2) / 2.0, fontSize,
                    widget.active ? LunacyMenuChrome.TEXT : 0xFF68746E);
        }
        ci.cancel();
    }
}
