package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the nine-slice button plate while preserving Minecraft's labels, icons and behavior. */
@Mixin(AbstractButton.class)
public abstract class PressableWidgetMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void lunacy$drawButton(GuiGraphicsExtractor context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!LunacyVanillaChrome.shouldTheme(client.gui.screen())) return;
        AbstractButton widget = (AbstractButton) (Object) this;
        lunacy$paintPlate(context, widget);
        double iconInset = widget instanceof SpriteIconButton ? 22 : 0;
        lunacy$paintLabel(context, widget, iconInset);
        ci.cancel();
    }

    @Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
    private void lunacy$drawLabel(ActiveTextCollector consumer, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!LunacyVanillaChrome.shouldTheme(client.gui.screen())) return;
        ci.cancel();
    }

    @Unique
    private static void lunacy$paintPlate(GuiGraphicsExtractor context, AbstractButton widget) {
        Minecraft client = Minecraft.getInstance();
        double x = widget.getX();
        double y = widget.getY();
        double width = widget.getWidth();
        double height = widget.getHeight();
        double radius = Math.min(12, Math.max(5, height / 2.0));
        boolean hovered = widget.isHovered() || widget.isFocused();
        boolean settings = LunacyVanillaChrome.isSettingsBranch(client.gui.screen());

        int outline = !widget.active ? 0x304A5C52 : hovered
                ? ColorUtil.withAlpha(LunacyMenuChrome.VIOLET, 185)
                : ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 70);
        UiRenderer.roundedRect(context, x, y, width, height, radius, outline);
        UiRenderer.roundedRect(context, x + 1, y + 1,
                Math.max(0, width - 2), Math.max(0, height - 2), Math.max(2, radius - 1),
                widget.active ? (hovered ? 0xF0223029 : settings ? 0xF018231D : 0xEC151F1A)
                        : 0xC00D1310);
        if (width > radius * 2 + 4) {
            UiRenderer.roundedRect(context, x + radius, y + 1, width - radius * 2, 1,
                    0.5, hovered ? 0x32FFFFFF : 0x18FFFFFF);
        }
        if (settings && widget.active) {
            int accent = hovered ? LunacyMenuChrome.VIOLET : LunacyMenuChrome.GREEN;
            UiRenderer.roundedRect(context, x + 7, y + height - 3, Math.max(0, width - 14), 1,
                    0.5, ColorUtil.withAlpha(accent, hovered ? 120 : 42));
        }
    }

    @Unique
    private static void lunacy$paintLabel(GuiGraphicsExtractor context, AbstractButton widget, double rightInset) {
        if (context == null) return;
        String raw = widget.getMessage().getString();
        if (raw == null || raw.isBlank()) return;
        boolean hovered = widget.isHovered() || widget.isFocused();
        double pad = Math.max(8, Math.min(12, widget.getWidth() * 0.06));
        double maxWidth = Math.max(6, widget.getWidth() - pad * 2 - rightInset);
        // Keep the caption inside the plate: ~30% of height, never taller than height - 8.
        double size = Math.min(widget.getHeight() * 0.30, widget.getHeight() - 8);
        size = Math.clamp(size, 5.4, 7.2);
        while (size > 5.4 && UiRenderer.fontWidth(FontFace.GOLOS_SEMI, raw, size) > maxWidth) {
            size -= 0.12;
        }
        String label = lunacy$fit(raw, maxWidth, size);
        double textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, size);
        double x = widget.getX() + Math.max(pad, (widget.getWidth() - rightInset - textWidth) / 2.0);
        if (x + textWidth > widget.getX() + widget.getWidth() - pad - rightInset) {
            x = widget.getX() + pad;
        }
        double y = widget.getY() + (widget.getHeight() - size) / 2.0;
        int color = !widget.active ? 0xFF68746E
                : hovered ? LunacyMenuChrome.TEXT : 0xFFE2E8E4;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, label, x, y, size, color);
    }

    @Unique
    private static String lunacy$fit(String value, double maxWidth, double size) {
        if (UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            if (UiRenderer.fontWidth(FontFace.GOLOS_SEMI, candidate, size) <= maxWidth) return candidate;
        }
        return suffix;
    }
}
