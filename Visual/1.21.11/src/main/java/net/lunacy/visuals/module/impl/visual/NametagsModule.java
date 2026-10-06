package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.user.LunacyUserService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;

/** Custom nametags that enhance vanilla 3D nametags with [LV] badge and health. */
public final class NametagsModule extends Module {
    private final BooleanSetting health = add(new BooleanSetting("health", "Health", "Show player health", true));
    private final BooleanSetting badges = add(new BooleanSetting("badges", "Badges", "Show the Lunacy LV badge", true));

    public NametagsModule() {
        super("nametags", "Custom Nametags", "Enhanced vanilla player nametags with badge and health",
                ModuleCategory.COSMETICS);
    }

    public Text formatNametag(PlayerEntity player, Text baseName) {
        if (baseName == null) return null;
        String baseStr = baseName.getString();
        MutableText tag = Text.empty();

        if (badges.get() && LunacyUserService.isLunacyUser(player.getUuid(), player.getName().getString())) {
            if (!baseStr.contains("[LV]")) {
                tag.append(LunacyUserService.badge());
            }
        }

        tag.append(baseName);

        if (health.get() && !baseStr.contains("HP")) {
            float hp = resolveHealth(player);
            int color = healthColor(player, hp);
            tag.append(Text.literal(String.format(Locale.ROOT, " %.0f HP", hp)).styled(s -> s.withColor(color)));
        }

        return tag;
    }

    public static float resolveHealth(PlayerEntity player) {
        if (player.getEntityWorld() != null) {
            Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
            if (scoreboard != null) {
                ScoreboardObjective belowName = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
                if (belowName != null) {
                    ReadableScoreboardScore score = scoreboard.getScore(player, belowName);
                    if (score != null && score.getScore() > 0) {
                        return score.getScore();
                    }
                }
                for (String objName : new String[]{"health", "hp", "Health", "HP", "healt"}) {
                    ScoreboardObjective obj = scoreboard.getNullableObjective(objName);
                    if (obj != null) {
                        ReadableScoreboardScore score = scoreboard.getScore(player, obj);
                        if (score != null && score.getScore() > 0) {
                            return score.getScore();
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

    private static int healthColor(PlayerEntity player, float hp) {
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
