package net.lunacy.visuals.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Add to lunacyvisuals.mixins.json client: "LunacyScreenLayoutMixin"
 *
 * After any Lunacy screen inits, push overlapping widgets apart.
 * Fixes the title-menu pile-up: Telegram icon + LV badge sitting on
 * "СЕТЕВАЯ ИГРА", and the two main buttons stacked too tight.
 *
 * Does not rewrite custom text (tickers). Those live in extractRenderState
 * of the menu class — send that file if subtitles still draw on buttons.
 */
@Mixin(Screen.class)
public abstract class LunacyScreenLayoutMixin {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "init()V", at = @At("RETURN"))
    private void lunacyvisuals$unstickWidgets(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        String name = screen.getClass().getSimpleName();
        if (!"LunacyTitleScreen".equals(name)) {
            return;
        }

        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getWidth() > 0 && widget.getHeight() > 0) {
                widgets.add(widget);
            }
        }
        if (widgets.size() < 2) {
            return;
        }

        widgets.sort(Comparator
                .comparingInt(AbstractWidget::getY)
                .thenComparingInt(AbstractWidget::getX)
                .thenComparingInt(AbstractWidget::getWidth));

        final int pad = 6;
        final int screenW = this.width;
        final int screenH = this.height;

        for (int i = 1; i < widgets.size(); i++) {
            AbstractWidget cur = widgets.get(i);
            for (int j = 0; j < i; j++) {
                AbstractWidget prev = widgets.get(j);
                if (!overlaps(prev, cur, pad)) {
                    continue;
                }
                boolean sameRow = Math.abs(cur.getY() - prev.getY()) < Math.max(prev.getHeight(), cur.getHeight()) / 2 + 2;
                if (sameRow && cur.getWidth() <= 48 && prev.getWidth() > 80) {
                    int x = prev.getX() - cur.getWidth() - pad;
                    if (x < 8) {
                        x = prev.getX() + prev.getWidth() + pad;
                    }
                    cur.setPosition(clamp(x, 8, screenW - cur.getWidth() - 8), prev.getY() + (prev.getHeight() - cur.getHeight()) / 2);
                } else if (sameRow) {
                    int x = prev.getX() + prev.getWidth() + pad;
                    if (x + cur.getWidth() > screenW - 8) {
                        cur.setPosition(prev.getX(), prev.getY() + prev.getHeight() + pad);
                    } else {
                        cur.setPosition(x, prev.getY());
                    }
                } else {
                    int y = prev.getY() + prev.getHeight() + pad;
                    if (y + cur.getHeight() > screenH - 8) {
                        y = screenH - cur.getHeight() - 8;
                    }
                    cur.setPosition(cur.getX(), y);
                }
            }
            int x = clamp(cur.getX(), 8, Math.max(8, screenW - cur.getWidth() - 8));
            int y = clamp(cur.getY(), 8, Math.max(8, screenH - cur.getHeight() - 8));
            cur.setPosition(x, y);
        }
    }

    private static boolean overlaps(AbstractWidget a, AbstractWidget b, int pad) {
        return a.getX() - pad < b.getX() + b.getWidth()
                && a.getX() + a.getWidth() + pad > b.getX()
                && a.getY() - pad < b.getY() + b.getHeight()
                && a.getY() + a.getHeight() + pad > b.getY();
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }
}
