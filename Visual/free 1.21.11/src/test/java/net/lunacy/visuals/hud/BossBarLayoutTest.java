package net.lunacy.visuals.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossBarLayoutTest {
    @Test
    void reportsNoOccupiedSpaceWithoutBars() {
        assertEquals(0, BossBarLayout.visibleBottom(0, 1080, false));
        assertEquals(0, BossBarLayout.visibleBottom(0, 1080, true));
    }

    @Test
    void followsVanillaSpacingAndTopThirdLimit() {
        assertEquals(17, BossBarLayout.visibleBottom(1, 1080, false));
        assertEquals(36, BossBarLayout.visibleBottom(2, 1080, false));
        assertEquals(359, BossBarLayout.visibleBottom(100, 1080, false));
    }

    @Test
    void followsCustomPanelSpacingAndTopThirdLimit() {
        assertEquals(32, BossBarLayout.visibleBottom(1, 1080, true));
        assertEquals(351, BossBarLayout.visibleBottom(100, 1080, true));
    }
}
