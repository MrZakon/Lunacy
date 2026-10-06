package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.gui.anim.ChatAnimation;
import net.lunacy.visuals.gui.anim.TabAnimation;
import net.lunacy.visuals.module.impl.hud.CustomCrosshairModule;
import net.lunacy.visuals.module.impl.visual.CustomChatModule;
import net.lunacy.visuals.gui.LunacyTheme;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Минимальные совместимые хуки вокруг отдельных ванильных HUD-элементов. */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Shadow @Final private PlayerTabOverlay tabList;
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$photoModeHud(GuiGraphicsExtractor context, DeltaTracker counter, CallbackInfo callback) {
        net.lunacy.visuals.module.impl.visual.PhotoModeModule photo = module(net.lunacy.visuals.module.impl.visual.PhotoModeModule.class);
        if (photo != null && photo.active()) callback.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$crosshair(GuiGraphicsExtractor context, DeltaTracker counter, CallbackInfo callback) {
        CustomCrosshairModule module = module(CustomCrosshairModule.class);
        if (module != null && module.isEnabled() && module.hidesVanilla()) callback.cancel();
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$animatedTab(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo callback) {
        ClientLevel level = this.minecraft.level;
        if (level == null) return;
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);

        boolean pressed = this.minecraft.options.keyPlayerList.isDown();
        boolean singleplayerNoObjective = this.minecraft.isLocalServer()
                && this.minecraft.player != null
                && this.minecraft.player.connection.getListedOnlinePlayers().size() <= 1
                && objective == null;
        boolean shouldOpen = pressed && !singleplayerNoObjective;

        double anim = TabAnimation.update(shouldOpen);
        if (anim > 0.001) {
            callback.cancel();
            this.tabList.setVisible(true);
            context.pose().pushMatrix();
            double offsetY = (anim - 1.0) * 22.0;
            double scale = 0.95 + 0.05 * anim;
            double centerX = context.guiWidth() / 2.0;
            context.pose().translate((float) centerX, 0.0f);
            context.pose().scale((float) scale, (float) scale);
            context.pose().translate((float) -centerX, (float) offsetY);

            this.tabList.extractRenderState(context, context.guiWidth(), scoreboard, objective);

            context.pose().popMatrix();
        } else {
            this.tabList.setVisible(false);
            if (shouldOpen) {
                callback.cancel();
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean lunacy$chatPushed = false;

    @Inject(method = "extractChat", at = @At("HEAD"))
    private void lunacyvisuals$beforeExtractChat(GuiGraphicsExtractor context, DeltaTracker counter, CallbackInfo callback) {
        double offsetY = 0.0;
        if (minecraft.screen instanceof ChatScreen) {
            double anim = ChatAnimation.getEased();
            offsetY = (1.0 - anim) * 24.0;
        } else {
            double msgAnim = ChatAnimation.getMessageAnim();
            if (msgAnim < 1.0) {
                offsetY = (1.0 - msgAnim) * 12.0;
            }
        }
        if (offsetY > 0.001) {
            context.pose().pushMatrix();
            context.pose().translate(0.0f, (float) offsetY);
            lunacy$chatPushed = true;
        } else {
            lunacy$chatPushed = false;
        }
    }

    @Inject(method = "extractChat", at = @At("RETURN"))
    private void lunacyvisuals$afterExtractChat(GuiGraphicsExtractor context, DeltaTracker counter, CallbackInfo callback) {
        if (lunacy$chatPushed) {
            context.pose().popMatrix();
            lunacy$chatPushed = false;
        }
    }

    private static <T extends net.lunacy.visuals.module.Module> T module(Class<T> type) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(type);
    }
}
