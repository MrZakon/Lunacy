package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.lunacy.visuals.user.LunacyUserService;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lunacyvisuals$animateChatMessage(Component message) {
        ChatAnimation.onMessageReceived();
        return message;
    }
}
