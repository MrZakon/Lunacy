package net.lunacy.visuals.hud;

public record HudPosition(HudAnchor anchor, double x, double y, double scale) {
    public HudPosition {
        anchor = anchor == null ? HudAnchor.TOP_LEFT : anchor;
        x = Math.clamp(x, -8192.0, 8192.0);
        y = Math.clamp(y, -8192.0, 8192.0);
        scale = Math.clamp(scale, 0.25, 4.0);
    }
}
