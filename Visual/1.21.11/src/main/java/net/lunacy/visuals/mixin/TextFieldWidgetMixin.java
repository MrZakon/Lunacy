package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMenuChrome;
import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded chrome for every native text field while preserving Minecraft's editing logic. */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void lunacy$roundedField(DrawContext context, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LunacyVanillaChrome.shouldTheme(client.currentScreen)) return;
        // Chat owns a bottom-aligned transparent input. Skinning it like an ordinary settings
        // field stretched the control across the whole screen and created the purple bottom bar.
        if (client.currentScreen instanceof ChatScreen) return;

        TextFieldWidget widget = (TextFieldWidget) (Object) this;
        widget.setDrawsBackground(false);
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
