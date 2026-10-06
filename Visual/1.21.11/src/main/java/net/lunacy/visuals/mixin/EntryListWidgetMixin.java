package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square native list plates and separator bands with one rounded Lunacy surface. */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {
    @Inject(method = "drawMenuListBackground", at = @At("HEAD"), cancellable = true)
    private void lunacy$roundedList(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null || LunacyVanillaChrome.isSettingsBranch(client.currentScreen)) return;
        if (!LunacyVanillaChrome.shouldTheme(client.currentScreen)) return;
        ClickableWidget widget = (ClickableWidget) (Object) this;
        int inset = 0;
        UiRenderer.roundedRect(context, widget.getX() + inset, widget.getY(), widget.getWidth() - inset * 2,
                widget.getHeight(), 16, 0xDC0E1713);
        UiRenderer.roundedBorder(context, widget.getX() + inset, widget.getY(), widget.getWidth() - inset * 2,
                widget.getHeight(), 16, 1, ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 60));
        ci.cancel();
    }

    @Inject(method = "drawHeaderAndFooterSeparators", at = @At("HEAD"), cancellable = true)
    private void lunacy$removeSeparators(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (LunacyVanillaChrome.shouldTheme(client.currentScreen)) ci.cancel();
    }
}
