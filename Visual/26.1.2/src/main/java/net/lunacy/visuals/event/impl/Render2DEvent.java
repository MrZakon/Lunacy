package net.lunacy.visuals.event.impl;

import net.lunacy.visuals.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Переиспользуемое событие отрисовки HUD без аллокации на каждый кадр. */
public final class Render2DEvent implements Event {
    private GuiGraphicsExtractor context;
    private float tickDelta;

    public Render2DEvent reset(GuiGraphicsExtractor context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
        return this;
    }

    public GuiGraphicsExtractor context() {
        return context;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
