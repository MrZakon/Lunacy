package net.lunacy.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Клиентская точка входа LunacyVisual.
 */
public final class LunacyVisuals implements ClientModInitializer {
    public static final String MOD_ID = "lunacyvisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("LunacyVisual bootstrap started");
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            boolean registered = ResourceLoader.registerBuiltinPack(
                    Identifier.fromNamespaceAndPath(MOD_ID, "punchy"), container,
                    Component.literal("Lunacy Punchy Animations"), PackActivationType.ALWAYS_ENABLED);
            if (!registered) LOGGER.warn("Could not register the bundled Punchy resource pack");
        });
        ClientRuntime.initialize();
        net.lunacy.visuals.holyworld.HolyWorldLiteApi.initialize();
    }
}
