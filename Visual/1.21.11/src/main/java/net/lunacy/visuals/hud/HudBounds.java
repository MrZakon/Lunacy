package net.lunacy.visuals.hud;

public record HudBounds(double x, double y, double width, double height) {
    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointY >= y && pointX < x + width && pointY < y + height;
    }
}
