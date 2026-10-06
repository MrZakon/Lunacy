package net.lunacy.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.TelemetryInfoScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.CodeOfConductScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.lunacy.visuals.render.UiRenderer;

import java.util.Locale;

/** Shared opt-in skin for Minecraft screens; container/inventory screens deliberately stay vanilla. */
public final class LunacyVanillaChrome {
    private LunacyVanillaChrome() {}

    public static boolean shouldTheme(Screen screen) {
        return screen != null && !(screen instanceof HandledScreen<?>) && !(screen instanceof ChatScreen)
                && !isSettingsBranch(screen) && !isOfficialMultiplayerDialog(screen);
    }

    public static boolean isSettingsBranch(Screen screen) {
        return screen instanceof OptionsScreen
                || screen instanceof GameOptionsScreen
                || screen instanceof PackScreen
                || screen instanceof TelemetryInfoScreen
                || screen instanceof CreditsAndAttributionScreen;
    }

    /** Native forms keep Minecraft's own font metrics, field padding and button labels. */
    private static boolean isOfficialMultiplayerDialog(Screen screen) {
        return screen instanceof AddServerScreen
                || screen instanceof DirectConnectScreen
                || screen instanceof MultiplayerWarningScreen
                || screen instanceof CodeOfConductScreen
                || screen instanceof SocialInteractionsScreen;
    }

    public static void renderBackground(Screen screen, DrawContext context,
                                        int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            LunacyMenuChrome.background(context, screen.width, screen.height, mouseX, mouseY, false);
        } else {
            LunacyMenuChrome.worldBackdrop(context, screen.width, screen.height);
        }
    }
}
