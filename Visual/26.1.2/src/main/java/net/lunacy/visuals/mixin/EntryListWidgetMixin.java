package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square native list plates and separator bands with one rounded Lunacy surface. */
@Mixin(AbstractSelectionList.class)
public abstract class EntryListWidgetMixin {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void lunacy$roundedList(GuiGraphicsExtractor context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null || LunacyVanillaChrome.isSettingsBranch(client.screen)) return;
        if (!LunacyVanillaChrome.shouldTheme(client.screen)) return;
        AbstractWidget widget = (AbstractWidget) (Object) this;
        int inset = 0;
        UiRenderer.roundedRect(context, widget.getX() + inset, widget.getY(), widget.getWidth() - inset * 2,
                widget.getHeight(), 16, 0xDC0E1713);
        UiRenderer.roundedBorder(context, widget.getX() + inset, widget.getY(), widget.getWidth() - inset * 2,
                widget.getHeight(), 16, 1, ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 60));
        ci.cancel();
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void lunacy$removeSeparators(GuiGraphicsExtractor context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (LunacyVanillaChrome.shouldTheme(client.screen)) ci.cancel();
    }
}
