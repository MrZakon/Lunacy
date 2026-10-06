package net.lunacy.visuals.event;

/** Базовое событие, которое обработчик может отменить. */
public abstract class CancellableEvent implements Event {
    private boolean cancelled;

    public final boolean isCancelled() {
        return cancelled;
    }

    public final void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public final void resetCancellation() {
        cancelled = false;
    }
}
