package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.KeyEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.minecraft.client.Minecraft;
public final class PhotoModeModule extends Module {
    public enum Filter { NATURAL, WARM, MOON, CINEMA }
    private final DoubleSetting fov=add(new DoubleSetting("fov","FOV","Угол обзора фоторежима",50,20,110,1));
    private final DoubleSetting roll=add(new DoubleSetting("roll","Наклон","Наклон камеры",0,-30,30,1));
    private final DoubleSetting time=add(new DoubleSetting("time","Время","Визуальное время мира",12500,0,24000,250));
    private final EnumSetting<Filter> filter=add(new EnumSetting<>("filter","Фильтр","Освещение кадра",Filter.NATURAL,Filter.class));
    private boolean active; private int oldFov; private double oldGamma; private long oldTime; private Object world;
    public PhotoModeModule(){super("photo_mode","Фоторежим","F8: камера, время, фильтр и чистый кадр; F2: снимок",ModuleCategory.RENDER_FX);}
    @Subscribe private void key(KeyEvent event){var c=Minecraft.getInstance();if(event.action()==1&&event.key()==297&&c.player!=null&&c.screen==null)setActive(!active,c);}
    @Subscribe private void tick(TickEvent.Client event){if(!active)return;var c=event.client();if(c.player==null||c.level==null){setActive(false,c);return;}c.options.fov().set(fov.get().intValue());c.options.gamma().set(gamma());c.level.setTimeFromServer(time.get().longValue());}
    private double gamma(){return switch(filter.get()){case NATURAL->oldGamma;case WARM->.65;case MOON->1.0;case CINEMA->.35;};}
    private void setActive(boolean value,Minecraft c){if(active==value)return;active=value;if(value){oldFov=c.options.fov().get();oldGamma=c.options.gamma().get();world=c.level;oldTime=world==null?0:c.level.getGameTime();}else{c.options.fov().set(oldFov);c.options.gamma().set(oldGamma);if(world!=null&&c.level==world)c.level.setTimeFromServer(oldTime);world=null;}}
    public boolean active(){return active;} public float roll(){return roll.get().floatValue();}
    @Override public String displayValue(){return active?"ACTIVE":filter.get().name();}
    @Override protected void onDisable(){if(active)setActive(false,Minecraft.getInstance());}
}
