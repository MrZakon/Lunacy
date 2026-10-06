package net.lunacy.visuals.event;

import net.lunacy.visuals.LunacyVisuals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Шина событий с reflection только в момент регистрации. Горячий dispatch работает по
 * неизменяемому массиву заранее связанных MethodHandle и не создаёт временных коллекций.
 */
public final class EventBus {
    private static final ListenerInvoker[] EMPTY = new ListenerInvoker[0];
    private static final Comparator<ListenerInvoker> PRIORITY_ORDER =
            Comparator.comparingInt(ListenerInvoker::priorityWeight).reversed();

    private final Object mutationLock = new Object();
    private final Map<Class<? extends Event>, ListenerInvoker[]> directListeners = new ConcurrentHashMap<>();
    private final Map<Class<?>, ListenerInvoker[]> dispatchCache = new ConcurrentHashMap<>();
    private final IdentityHashMap<Object, List<ListenerInvoker>> listenersByOwner = new IdentityHashMap<>();

    public Subscription register(Object owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Event listener owner cannot be null");
        }

        List<ListenerInvoker> discovered = discover(owner);
        synchronized (mutationLock) {
            unregisterLocked(owner);
            listenersByOwner.put(owner, discovered);

            for (ListenerInvoker invoker : discovered) {
                ListenerInvoker[] previous = directListeners.getOrDefault(invoker.eventType(), EMPTY);
                ListenerInvoker[] next = Arrays.copyOf(previous, previous.length + 1);
                next[previous.length] = invoker;
                Arrays.sort(next, PRIORITY_ORDER);
                directListeners.put(invoker.eventType(), next);
            }
            dispatchCache.clear();
        }
        return new Subscription(this, owner);
    }

    public void unregister(Object owner) {
        if (owner == null) {
            return;
        }
        synchronized (mutationLock) {
            unregisterLocked(owner);
            dispatchCache.clear();
        }
    }

    public <T extends Event> T post(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        ListenerInvoker[] listeners = dispatchCache.computeIfAbsent(event.getClass(), this::buildDispatchArray);
        for (ListenerInvoker listener : listeners) {
            try {
                listener.invoke(event);
            } catch (Throwable throwable) {
                LunacyVisuals.LOGGER.error(
                        "Event listener {} failed while handling {}",
                        listener.owner().getClass().getName(),
                        event.getClass().getName(),
                        throwable
                );
            }
        }
        return event;
    }

    public int listenerCount() {
        synchronized (mutationLock) {
            return listenersByOwner.values().stream().mapToInt(List::size).sum();
        }
    }

    private void unregisterLocked(Object owner) {
        List<ListenerInvoker> previous = listenersByOwner.remove(owner);
        if (previous == null) {
            return;
        }

        for (ListenerInvoker removed : previous) {
            ListenerInvoker[] current = directListeners.getOrDefault(removed.eventType(), EMPTY);
            ListenerInvoker[] next = Arrays.stream(current)
                    .filter(candidate -> candidate != removed)
                    .toArray(ListenerInvoker[]::new);
            if (next.length == 0) {
                directListeners.remove(removed.eventType());
            } else {
                directListeners.put(removed.eventType(), next);
            }
        }
    }

    private ListenerInvoker[] buildDispatchArray(Class<?> concreteEventType) {
        ArrayList<ListenerInvoker> result = new ArrayList<>();
        directListeners.forEach((registeredType, listeners) -> {
            if (registeredType.isAssignableFrom(concreteEventType)) {
                result.addAll(Arrays.asList(listeners));
            }
        });
        result.sort(PRIORITY_ORDER);
        return result.toArray(ListenerInvoker[]::new);
    }

    private static List<ListenerInvoker> discover(Object owner) {
        ArrayList<ListenerInvoker> result = new ArrayList<>();
        Class<?> cursor = owner.getClass();

        while (cursor != null && cursor != Object.class) {
            MethodHandles.Lookup lookup;
            try {
                lookup = MethodHandles.privateLookupIn(cursor, MethodHandles.lookup());
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("Cannot create lookup for " + cursor.getName(), exception);
            }

            for (Method method : cursor.getDeclaredMethods()) {
                Subscribe subscribe = method.getAnnotation(Subscribe.class);
                if (subscribe == null || method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || method.getParameterCount() != 1
                        || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalArgumentException("Invalid @Subscribe method: " + method);
                }

                @SuppressWarnings("unchecked")
                Class<? extends Event> eventType = (Class<? extends Event>) method.getParameterTypes()[0];
                try {
                    MethodHandle handle = lookup.unreflect(method)
                            .bindTo(owner)
                            .asType(MethodType.methodType(void.class, Event.class));
                    result.add(new ListenerInvoker(
                            owner,
                            eventType,
                            subscribe.priority(),
                            subscribe.receiveCancelled(),
                            handle
                    ));
                } catch (IllegalAccessException exception) {
                    throw new IllegalArgumentException("Cannot bind @Subscribe method: " + method, exception);
                }
            }
            cursor = cursor.getSuperclass();
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(owner.getClass().getName() + " has no @Subscribe methods");
        }
        result.sort(PRIORITY_ORDER);
        return List.copyOf(result);
    }
}
