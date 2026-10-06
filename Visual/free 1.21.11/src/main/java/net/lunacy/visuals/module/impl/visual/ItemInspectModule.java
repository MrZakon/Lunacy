package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.KeyEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
public final class ItemInspectModule extends Module {
    private final DoubleSetting key=add(new DoubleSetting("key","Клавиша (GLFW)","По умолчанию G = 71",71,32,348,1));
    private final DoubleSetting duration=add(new DoubleSetting("duration","Длительность","Продолжительность осмотра",1.8,.6,4,.1));
    private long started;
    private ItemStack held;
    public ItemInspectModule(){super("item_inspect","Осмотр предмета","G: осмотреть предмет; использование прерывает анимацию",ModuleCategory.RENDER_FX);}
    @Subscribe private void key(KeyEvent event){
        var client=MinecraftClient.getInstance();
        if(event.action()==1 && event.key()==key.get().intValue() && client.currentScreen==null && client.player!=null && !client.player.getMainHandStack().isEmpty()){
            held=client.player.getMainHandStack().copy();started=System.nanoTime();
        }
    }
    @Subscribe private void tick(TickEvent.Client event){
        var client=event.client();
        if(client.player==null || client.currentScreen!=null || client.player.isUsingItem() || client.player.getHandSwingProgress(1)>0 || held==null || !ItemStack.areItemsEqual(held,client.player.getMainHandStack()))started=0;
    }
    public float progress(){
        if(!isEnabled() || started==0)return 0;
        double t=(System.nanoTime()-started)/1e9/duration.get();
        if(t>=1){started=0;return 0;}return (float)t;
    }
    public float roll(float t){
        if(held==null)return 0;
        Item item=held.getItem();
        if(item instanceof BowItem)return (float)Math.sin(t*Math.PI)*-65;
        if(item instanceof AxeItem)return (float)Math.sin(t*Math.PI)*110;
        if(item==Items.TOTEM_OF_UNDYING)return (float)Math.sin(t*Math.PI*2)*35;
        return (float)(360*(t*t*(3-2*t)));
    }
    @Override protected void onDisable(){started=0;held=null;}
}
