package net.lunacy.visuals.hud;

/** Exact vertical bounds used by vanilla and Lunacy boss-bar renderers. */
public final class BossBarLayout {
    private static final int VANILLA_START_Y = 12;
    private static final int VANILLA_STEP = 19;
    private static final int VANILLA_HEIGHT = 5;
    private static final int CUSTOM_START_Y = 8;
    private static final int CUSTOM_STEP = 29;
    private static final int CUSTOM_HEIGHT = 24;

    private BossBarLayout() {
    }

    public static int visibleBottom(int barCount, int screenHeight, boolean custom) {
        if (barCount <= 0 || screenHeight <= 0) return 0;
        return custom ? customBottom(barCount, screenHeight) : vanillaBottom(barCount, screenHeight);
    }

    private static int vanillaBottom(int barCount, int screenHeight) {
        int y = VANILLA_START_Y;
        int bottom = 0;
        int limit = screenHeight / 3;
        for (int index = 0; index < barCount; index++) {
            bottom = y + VANILLA_HEIGHT;
            y += VANILLA_STEP;
            if (y >= limit) break;
        }
        return bottom;
    }

    private static int customBottom(int barCount, int screenHeight) {
        int y = CUSTOM_START_Y;
        int bottom = 0;
        double limit = screenHeight / 3.0;
        for (int index = 0; index < barCount; index++) {
            if (y + CUSTOM_HEIGHT + 1 > limit) break;
            bottom = y + CUSTOM_HEIGHT;
            y += CUSTOM_STEP;
        }
        return bottom;
    }
}
