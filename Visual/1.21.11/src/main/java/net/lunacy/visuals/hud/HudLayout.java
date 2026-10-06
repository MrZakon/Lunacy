package net.lunacy.visuals.hud;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Снимок границ HUD текущего кадра для drag-and-drop редактора. */
public final class HudLayout {
    private final Map<String, HudBounds> bounds = new LinkedHashMap<>();

    public void beginFrame() { bounds.clear(); }
    public void put(String id, HudBounds value) { bounds.put(id, value); }
    public Map<String, HudBounds> bounds() { return Collections.unmodifiableMap(bounds); }
}
