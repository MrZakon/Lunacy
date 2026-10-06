package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.lunacy.visuals.user.LunacyUserService;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 0)
    private Text lunacyvisuals$animateChatMessage(Text message) {
        ChatAnimation.onMessageReceived();
        return message;
    }
}
