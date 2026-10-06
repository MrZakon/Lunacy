package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyPauseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenRedirectMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void lunacy$openCustomPause(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof PauseScreen) client.setScreen(new LunacyPauseScreen());
        });
    }
}
