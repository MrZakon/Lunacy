package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Передаёт GLFW-клавиши в шину событий до ванильной обработки. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$onKey(long window, int action, KeyEvent input, CallbackInfo callback) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onKey(input.key(), input.scancode(), action, input.modifiers())) {
            callback.cancel();
        }
    }
}
