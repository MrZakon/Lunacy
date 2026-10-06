package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyPauseScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenRedirectMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void lunacy$openCustomPause(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.currentScreen instanceof GameMenuScreen) client.setScreen(new LunacyPauseScreen());
        });
    }
}
