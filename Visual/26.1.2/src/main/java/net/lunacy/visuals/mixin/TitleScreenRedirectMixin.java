package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens the custom menu on the same initialization cycle, matching the supplied source. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenRedirectMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void lunacy$openCustomTitle(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof TitleScreen) client.setScreen(new LunacyTitleScreen());
        });
    }
}
