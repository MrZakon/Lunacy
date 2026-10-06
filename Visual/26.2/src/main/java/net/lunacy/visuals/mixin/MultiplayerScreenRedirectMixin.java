package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyMultiplayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps every vanilla entry path, including F5 refresh, inside the fixed multiplayer shell. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenRedirectMixin {
    @Shadow @Final private Screen lastScreen;

    @Inject(method = "init", at = @At("RETURN"))
    private void lunacy$keepCustomMultiplayer(CallbackInfo ci) {
        if ((Object) this instanceof LunacyMultiplayerScreen) return;
        Minecraft client = Minecraft.getInstance();
        Screen vanillaScreen = (Screen) (Object) this;
        client.execute(() -> {
            if (client.gui.screen() == vanillaScreen) {
                client.gui.setScreen(new LunacyMultiplayerScreen(lastScreen));
            }
        });
    }
}
