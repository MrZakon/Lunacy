package net.lunacy.visuals.gui;

import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import java.util.Locale;

/** Shared opt-in skin for Minecraft screens; container/inventory screens deliberately stay vanilla. */
public final class LunacyVanillaChrome {
    private LunacyVanillaChrome() {}

    public static boolean shouldTheme(Screen screen) {
        return screen != null && !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof ChatScreen)
                && !isSettingsBranch(screen) && !isOfficialMultiplayerDialog(screen);
    }

    public static boolean isSettingsBranch(Screen screen) {
        return screen instanceof OptionsScreen
                || screen instanceof OptionsSubScreen
                || screen instanceof PackSelectionScreen
                || screen instanceof TelemetryInfoScreen
                || screen instanceof CreditsAndAttributionScreen;
    }

    /** Native forms keep Minecraft's own font metrics, field padding and button labels. */
    private static boolean isOfficialMultiplayerDialog(Screen screen) {
        return screen instanceof ManageServerScreen
                || screen instanceof DirectJoinServerScreen
                || screen instanceof SafetyScreen
                || screen instanceof CodeOfConductScreen
                || screen instanceof SocialInteractionsScreen;
    }

    public static void renderBackground(Screen screen, GuiGraphicsExtractor context,
                                        int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            LunacyMenuChrome.background(context, screen.width, screen.height, mouseX, mouseY, false);
        } else {
            LunacyMenuChrome.worldBackdrop(context, screen.width, screen.height);
        }
    }
}
