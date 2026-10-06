package net.lunacy.visuals.event.impl;

import net.lunacy.visuals.event.Event;
import net.minecraft.client.Minecraft;

public final class TickEvent {
    private TickEvent() {}

    /** Переиспользуемое событие конца клиентского тика. */
    public static final class Client implements Event {
        private Minecraft client;

        public Client reset(Minecraft client) {
            this.client = client;
            return this;
        }

        public Minecraft client() { return client; }
    }
}
