package net.lunacy.visuals.event;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Subscription implements AutoCloseable {
    private final EventBus bus;
    private final Object owner;
    private final AtomicBoolean closed = new AtomicBoolean();

    Subscription(EventBus bus, Object owner) {
        this.bus = bus;
        this.owner = owner;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            bus.unregister(owner);
        }
    }
}
