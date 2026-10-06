package net.lunacy.visuals.event.impl;

import net.lunacy.visuals.event.CancellableEvent;

public final class MouseEvent extends CancellableEvent {
    public enum Type { BUTTON, SCROLL }

    private final Type type;
    private final int button;
    private final int action;
    private final int modifiers;
    private final double horizontal;
    private final double vertical;

    private MouseEvent(Type type, int button, int action, int modifiers, double horizontal, double vertical) {
        this.type = type;
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static MouseEvent button(int button, int action, int modifiers) {
        return new MouseEvent(Type.BUTTON, button, action, modifiers, 0.0, 0.0);
    }

    public static MouseEvent scroll(double horizontal, double vertical) {
        return new MouseEvent(Type.SCROLL, -1, -1, 0, horizontal, vertical);
    }

    public Type type() { return type; }
    public int button() { return button; }
    public int action() { return action; }
    public int modifiers() { return modifiers; }
    public double horizontal() { return horizontal; }
    public double vertical() { return vertical; }
}
