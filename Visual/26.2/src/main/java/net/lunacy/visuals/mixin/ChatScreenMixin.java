package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private void lunacy$onInitChat(CallbackInfo ci) {
        ChatAnimation.reset();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void lunacy$renderChatHead(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        double anim = ChatAnimation.update(true);
        double offsetY = (1.0 - anim) * 24.0;
        context.pose().pushMatrix();
        context.pose().translate(0.0f, (float) offsetY);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void lunacy$renderChatReturn(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.pose().popMatrix();
        net.lunacy.visuals.hud.HudEditorHandler.render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lunacy$onMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (net.lunacy.visuals.hud.HudEditorHandler.mouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void lunacy$onCloseChat(CallbackInfo ci) {
        ChatAnimation.reset();
        net.lunacy.visuals.hud.HudEditorHandler.onMouseRelease();
    }
}
