package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and displays totem pops for enemy players.
 */
public final class TotemCounterModule extends Module {
    private static final Map<UUID, Integer> POPS = new ConcurrentHashMap<>();

    private final BooleanSetting showNametags = add(new BooleanSetting(
            "nametags", "Nametag Counter", "Show popped totems above player nametag", true));
    private final BooleanSetting chatNotify = add(new BooleanSetting(
            "chat_notify", "Chat Notification", "Print a stylish notification when a totem is popped", false));

    public TotemCounterModule() {
        super("totem_counter", "Totem Counter", "Tracks and counts enemy popped totems",
                ModuleCategory.HUD);
        setEnabled(true);
    }

    public static void onPop(UUID uuid, String name) {
        if (uuid == null) return;
        int count = POPS.merge(uuid, 1, Integer::sum);
    }

    public static void onDeath(UUID uuid) {
        if (uuid == null) return;
        POPS.remove(uuid);
    }

    public static int getPops(UUID uuid) {
        if (uuid == null) return 0;
        return POPS.getOrDefault(uuid, 0);
    }

    public boolean showNametags() { return showNametags.get(); }
    public boolean chatNotify() { return chatNotify.get(); }
}