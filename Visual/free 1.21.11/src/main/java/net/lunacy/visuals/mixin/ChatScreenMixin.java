package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
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

    @Inject(method = "render", at = @At("HEAD"))
    private void lunacy$renderChatHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        double anim = ChatAnimation.update(true);
        double offsetY = (1.0 - anim) * 24.0;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, (float) offsetY);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lunacy$renderChatReturn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.getMatrices().popMatrix();
        net.lunacy.visuals.hud.HudEditorHandler.render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lunacy$onMouseClicked(net.minecraft.client.gui.Click click, boolean doubled, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
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
