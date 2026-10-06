package net.lunacy.visuals.event;

import java.lang.invoke.MethodHandle;

final class ListenerInvoker {
    private final Object owner;
    private final Class<? extends Event> eventType;
    private final EventPriority priority;
    private final boolean receiveCancelled;
    private final MethodHandle handle;

    ListenerInvoker(
            Object owner,
            Class<? extends Event> eventType,
            EventPriority priority,
            boolean receiveCancelled,
            MethodHandle handle
    ) {
        this.owner = owner;
        this.eventType = eventType;
        this.priority = priority;
        this.receiveCancelled = receiveCancelled;
        this.handle = handle;
    }

    Object owner() {
        return owner;
    }

    Class<? extends Event> eventType() {
        return eventType;
    }

    int priorityWeight() {
        return priority.weight();
    }

    void invoke(Event event) throws Throwable {
        if (event instanceof CancellableEvent cancellable && cancellable.isCancelled() && !receiveCancelled) {
            return;
        }
        handle.invokeExact(event);
    }
}
