package net.lunacy.visuals.user;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players using Lunacy Visuals and provides the [LV] badge across Nametags, Tab, and Chat.
 */
public final class LunacyUserService {
    private static final Set<String> KNOWN_USERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> KNOWN_UUIDS = ConcurrentHashMap.newKeySet();

    private LunacyUserService() {}

    public static void registerUser(String name, UUID uuid) {
        if (name != null && !name.isBlank()) {
            KNOWN_USERS.add(name.toLowerCase(Locale.ROOT).trim());
        }
        if (uuid != null) {
            KNOWN_UUIDS.add(uuid);
        }
    }

    public static boolean isLunacyUser(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (uuid != null && uuid.equals(client.player.getUuid())) return true;
            if (name != null && name.equalsIgnoreCase(client.player.getName().getString())) return true;
        }
        if (uuid != null && KNOWN_UUIDS.contains(uuid)) return true;
        if (name != null && KNOWN_USERS.contains(name.toLowerCase(Locale.ROOT).trim())) return true;
        return false;
    }

    public static MutableText badge() {
        return Text.literal("[LV] ").styled(style -> style
                .withColor(0xB18CFF)
                .withBold(true));
    }
}
