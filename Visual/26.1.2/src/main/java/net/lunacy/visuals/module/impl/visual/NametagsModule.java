package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.user.LunacyUserService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;

/** Custom nametags that enhance vanilla 3D nametags with [LV] badge and health. */
public final class NametagsModule extends Module {
    private final BooleanSetting health = add(new BooleanSetting("health", "Health", "Show player health", true));
    private final BooleanSetting badges = add(new BooleanSetting("badges", "Badges", "Show the Lunacy LV badge", true));

    public NametagsModule() {
        super("nametags", "Custom Nametags", "Enhanced vanilla player nametags with badge and health",
                ModuleCategory.COSMETICS);
    }

    public Component formatNametag(Player player, Component baseName) {
        if (baseName == null) return null;
        String baseStr = baseName.getString();
        MutableComponent tag = Component.empty();

        if (badges.get() && LunacyUserService.isLunacyUser(player.getUUID(), player.getName().getString())) {
            if (!baseStr.contains("[LV]")) {
                tag.append(LunacyUserService.badge());
            }
        }

        tag.append(baseName);

        if (health.get() && !baseStr.contains("HP")) {
            float hp = resolveHealth(player);
            int color = healthColor(player, hp);
            tag.append(Component.literal(String.format(Locale.ROOT, " %.0f HP", hp)).withStyle(s -> s.withColor(color)));
        }

        return tag;
    }

    public static float resolveHealth(Player player) {
        if (player.level() != null) {
            Scoreboard scoreboard = player.level().getScoreboard();
            if (scoreboard != null) {
                Objective belowName = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
                if (belowName != null) {
                    ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(player, belowName);
                    if (score != null && score.value() > 0) {
                        return score.value();
                    }
                }
                for (String objName : new String[]{"health", "hp", "Health", "HP", "healt"}) {
                    Objective obj = scoreboard.getObjective(objName);
                    if (obj != null) {
                        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(player, obj);
                        if (score != null && score.value() > 0) {
                            return score.value();
                        }
                    }
                }
            }
        }

        float hp = player.getHealth();
        float maxHp = player.getMaxHealth();
        float absorption = player.getAbsorptionAmount();

        if (maxHp > 40.0F && hp > 0.0F) {
            hp = (hp / maxHp) * 20.0F + absorption;
        } else if (hp > maxHp + absorption) {
            hp = maxHp + absorption;
        } else {
            hp = hp + absorption;
        }

        return Math.max(0.0F, hp);
    }

    private static int healthColor(Player player, float hp) {
        float maximum = player.getMaxHealth();
        if (maximum > 40.0F) {
            maximum = 20.0F;
        }
        maximum = Math.max(1.0F, maximum);
        float fraction = Math.clamp(hp / maximum, 0.0F, 1.0F);
        if (fraction > 0.60F) return 0xFF55FF55;
        if (fraction > 0.30F) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
