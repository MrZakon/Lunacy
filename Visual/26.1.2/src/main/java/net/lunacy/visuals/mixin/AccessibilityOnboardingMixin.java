package net.lunacy.visuals.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Add to lunacyvisuals.mixins.json client: "AccessibilityOnboardingMixin"
 *
 * Relayout the 2x2 first-launch buttons so Russian labels
 * ("Специальные возможности", "Музыка и звуки") do not run into the
 * trailing icons.
 */
@Mixin(AccessibilityOnboardingScreen.class)
public abstract class AccessibilityOnboardingMixin extends Screen {
    protected AccessibilityOnboardingMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void lunacyvisuals$relayoutOnboarding(CallbackInfo ci) {
        List<AbstractWidget> grid = new ArrayList<>();
        for (var child : this.children()) {
            if (child instanceof SpriteIconButton sprite) {
                grid.add(sprite);
            } else if (child instanceof Button button
                    && button.getHeight() >= 16
                    && button.getHeight() <= 28
                    && button.getWidth() >= 80
                    && button.getWidth() <= 280) {
                grid.add(button);
            }
        }
        if (grid.size() < 4) {
            return;
        }
        grid.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));
        List<AbstractWidget> cells = grid.subList(0, 4);

        int margin = Math.max(20, this.width / 18);
        int gapX = 12;
        int gapY = 10;
        int colW = Math.max(220, (this.width - margin * 2 - gapX) / 2);
        int rowH = Math.max(20, cells.get(0).getHeight());
        int totalH = rowH * 2 + gapY;
        int startY = Math.max(this.height / 2 - totalH / 2, this.height / 3);
        int leftX = this.width / 2 - gapX / 2 - colW;
        int rightX = this.width / 2 + gapX / 2;

        place(cells.get(0), leftX, startY, colW, rowH);
        place(cells.get(1), rightX, startY, colW, rowH);
        place(cells.get(2), leftX, startY + rowH + gapY, colW, rowH);
        place(cells.get(3), rightX, startY + rowH + gapY, colW, rowH);
    }

    private static void place(AbstractWidget widget, int x, int y, int w, int h) {
        widget.setRectangle(w, h, x, y);
    }
}
