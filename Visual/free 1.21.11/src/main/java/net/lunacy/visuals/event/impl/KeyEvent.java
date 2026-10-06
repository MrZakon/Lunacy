package net.lunacy.visuals.event.impl;

import net.lunacy.visuals.event.CancellableEvent;

public final class KeyEvent extends CancellableEvent {
    private final int key;
    private final int scanCode;
    private final int action;
    private final int modifiers;

    public KeyEvent(int key, int scanCode, int action, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int key() { return key; }
    public int scanCode() { return scanCode; }
    public int action() { return action; }
    public int modifiers() { return modifiers; }
}
