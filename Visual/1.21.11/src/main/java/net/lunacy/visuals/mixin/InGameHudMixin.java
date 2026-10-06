package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.lunacy.visuals.gui.anim.TabAnimation;
import net.lunacy.visuals.module.impl.hud.CustomCrosshairModule;
import net.lunacy.visuals.module.impl.visual.CustomChatModule;
import net.lunacy.visuals.gui.LunacyTheme;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Минимальные совместимые хуки вокруг отдельных ванильных HUD-элементов. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private PlayerListHud playerListHud;
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$crosshair(DrawContext context, RenderTickCounter counter, CallbackInfo callback) {
        CustomCrosshairModule module = module(CustomCrosshairModule.class);
        if (module != null && module.isEnabled() && module.hidesVanilla()) callback.cancel();
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$animatedTab(DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        ClientWorld world = this.client.world;
        if (world == null) return;
        Scoreboard scoreboard = world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);

        boolean pressed = this.client.options.playerListKey.isPressed();
        boolean singleplayerNoObjective = this.client.isInSingleplayer()
                && this.client.player != null
                && this.client.player.networkHandler.getListedPlayerListEntries().size() <= 1
                && objective == null;
        boolean shouldOpen = pressed && !singleplayerNoObjective;

        double anim = TabAnimation.update(shouldOpen);
        if (anim > 0.001) {
            callback.cancel();
            this.playerListHud.setVisible(true);
            context.getMatrices().pushMatrix();
            double offsetY = (anim - 1.0) * 22.0;
            double scale = 0.95 + 0.05 * anim;
            double centerX = context.getScaledWindowWidth() / 2.0;
            context.getMatrices().translate((float) centerX, 0.0f);
            context.getMatrices().scale((float) scale, (float) scale);
            context.getMatrices().translate((float) -centerX, (float) offsetY);

            this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);

            context.getMatrices().popMatrix();
        } else {
            this.playerListHud.setVisible(false);
            if (shouldOpen) {
                callback.cancel();
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean lunacy$chatPushed = false;

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void lunacyvisuals$beforeRenderChat(DrawContext context, RenderTickCounter counter, CallbackInfo callback) {
        double offsetY = 0.0;
        if (client.currentScreen instanceof ChatScreen) {
            double anim = ChatAnimation.getEased();
            offsetY = (1.0 - anim) * 24.0;
        } else {
            double msgAnim = ChatAnimation.getMessageAnim();
            if (msgAnim < 1.0) {
                offsetY = (1.0 - msgAnim) * 12.0;
            }
        }
        if (offsetY > 0.001) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, (float) offsetY);
            lunacy$chatPushed = true;
        } else {
            lunacy$chatPushed = false;
        }
    }

    @Inject(method = "renderChat", at = @At("RETURN"))
    private void lunacyvisuals$afterRenderChat(DrawContext context, RenderTickCounter counter, CallbackInfo callback) {
        if (lunacy$chatPushed) {
            context.getMatrices().popMatrix();
            lunacy$chatPushed = false;
        }
    }

    private static <T extends net.lunacy.visuals.module.Module> T module(Class<T> type) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(type);
    }
}
