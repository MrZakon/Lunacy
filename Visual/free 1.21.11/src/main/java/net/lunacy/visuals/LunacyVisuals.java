package net.lunacy.visuals;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Клиентская точка входа LunacyVisual — FREE edition.
 */
public final class LunacyVisuals implements ClientModInitializer {
    public static final String MOD_ID = "lunacyvisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("LunacyVisual FREE bootstrap started");
        ClientRuntime.initialize();
        net.lunacy.visuals.holyworld.HolyWorldLiteApi.initialize();
    }
}
