package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.KeyEvent;
import net.lunacy.visuals.gui.CosmeticPreviewScreen;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.minecraft.client.Minecraft;
public final class CosmeticPreviewModule extends Module {
    public CosmeticPreviewModule(){super("cosmetic_preview","Примерочная","K: осмотр и настройка косметики на персонаже",ModuleCategory.COSMETICS);}
    @Subscribe private void key(KeyEvent event){var c=Minecraft.getInstance();if(event.action()==1&&event.key()==75&&c.player!=null&&c.screen==null)c.setScreen(new CosmeticPreviewScreen(null));}
}
