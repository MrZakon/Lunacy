package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.NametagsModule;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void lunacyvisuals$updatePlayerNametag(Avatar avatar, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        NametagsModule module = runtime.module(NametagsModule.class);
        if (module != null && module.isEnabled() && avatar instanceof Player player) {
            Component baseName = player.getDisplayName();
            state.nameTag = module.formatNametag(player, baseName);
        }
    }
}
