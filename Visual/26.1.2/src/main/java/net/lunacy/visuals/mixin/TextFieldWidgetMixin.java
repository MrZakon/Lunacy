package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded chrome for every native text field while preserving Minecraft's editing logic. */
@Mixin(EditBox.class)
public abstract class TextFieldWidgetMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void lunacy$roundedField(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!LunacyVanillaChrome.shouldTheme(client.screen)) return;
        // Chat owns a bottom-aligned transparent input. Skinning it like an ordinary settings
        // field stretched the control across the whole screen and created the purple bottom bar.
        if (client.screen instanceof ChatScreen) return;

        EditBox widget = (EditBox) (Object) this;
        widget.setBordered(false);
        double radius = Math.min(12, Math.max(6, widget.getHeight() / 2.0));
        boolean focused = widget.isFocused();
        UiRenderer.roundedRect(context,
                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), radius,
                focused ? 0xF0202D27 : 0xED151F1A);
        UiRenderer.roundedBorder(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                radius, 1, ColorUtil.withAlpha(focused ? LunacyMenuChrome.VIOLET : LunacyMenuChrome.GREEN,
                        focused ? 175 : 65));
    }
}
