package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.NametagsModule;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void lunacyvisuals$updatePlayerNametag(PlayerLikeEntity playerLike, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        NametagsModule module = runtime.module(NametagsModule.class);
        if (module != null && module.isEnabled() && playerLike instanceof PlayerEntity player) {
            if (state.nameLabelPos != null) {
                net.minecraft.text.Text base = state.displayName != null ? state.displayName : player.getDisplayName();
                state.displayName = module.formatNametag(player, base);
                state.playerName = null;
            }
        }
    }
}
