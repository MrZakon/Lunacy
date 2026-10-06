package net.lunacy.visuals.event.impl;

import net.lunacy.visuals.event.Event;
import net.minecraft.client.MinecraftClient;

public final class TickEvent {
    private TickEvent() {}

    /** Переиспользуемое событие конца клиентского тика. */
    public static final class Client implements Event {
        private MinecraftClient client;

        public Client reset(MinecraftClient client) {
            this.client = client;
            return this;
        }

        public MinecraftClient client() { return client; }
    }
}
