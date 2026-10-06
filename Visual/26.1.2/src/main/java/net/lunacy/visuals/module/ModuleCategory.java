package net.lunacy.visuals.module;

public enum ModuleCategory {
    COSMETICS("Cosmetics"),
    HUD("HUD"),
    RENDER_FX("Render FX"),
    WORLD_VISUALS("Visual Settings"),
    SETTINGS("Settings");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
