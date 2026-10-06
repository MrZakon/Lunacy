package net.lunacy.visuals.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    @Test
    void dispatchesInPriorityOrderAndSupportsUnregister() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        Object listener = new Object() {
            @Subscribe(priority = EventPriority.LOW)
            private void low(TestEvent event) {
                calls.add("low");
            }

            @Subscribe(priority = EventPriority.HIGH)
            private void high(TestEvent event) {
                calls.add("high");
            }
        };

        Subscription subscription = bus.register(listener);
        bus.post(new TestEvent());
        assertEquals(List.of("high", "low"), calls);

        subscription.close();
        bus.post(new TestEvent());
        assertEquals(List.of("high", "low"), calls);
    }

    @Test
    void cancelledEventsOnlyReachOptedInListeners() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.register(new Object() {
            @Subscribe(priority = EventPriority.HIGH)
            private void cancel(CancelEvent event) {
                calls.add("cancel");
                event.setCancelled(true);
            }

            @Subscribe
            private void skipped(CancelEvent event) {
                calls.add("skipped");
            }

            @Subscribe(priority = EventPriority.LOW, receiveCancelled = true)
            private void audit(CancelEvent event) {
                calls.add("audit");
            }
        });

        CancelEvent event = bus.post(new CancelEvent());
        assertTrue(event.isCancelled());
        assertEquals(List.of("cancel", "audit"), calls);
    }

    private static final class TestEvent implements Event {
    }

    private static final class CancelEvent extends CancellableEvent {
    }
}
