package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.item.Items;
public final class WeaponTrailsModule extends Module {
    public enum Style { CRESCENT, FIRE, ICE }
    private final EnumSetting<Style> style=add(new EnumSetting<>("style","Стиль","Полумесяц, огонь или лёд",Style.CRESCENT,Style.class));
    private final ColorSetting color=add(new ColorSetting("color","Цвет","Цвет разреза",new ColorValue(0xFF98EDFF,0xFFA878FF,false,.7)));
    private final DoubleSetting width=add(new DoubleSetting("width","Ширина","Ширина дуги",1.3,.4,2.5,.1));
    private final DoubleSetting duration=add(new DoubleSetting("duration","Длительность","Длительность разреза в тиках",5,2,10,1));
    private float previous;
    private int age=99;
    private Object world;
    public WeaponTrailsModule(){super("weapon_trails","След оружия","Цветная дуга за взмахом оружия",ModuleCategory.RENDER_FX);}
    @Subscribe private void tick(TickEvent.Client event){
        var client=event.client();
        if(client.player==null || client.world==null){reset();return;}
        if(world!=client.world){reset();world=client.world;}
        var p=client.player;
        float swing=p.getHandSwingProgress(1);
        if(swing>0 && (previous==0 || swing<previous) && !p.getMainHandStack().isEmpty())age=0;
        previous=swing;
        if(age++>=duration.get())return;
        double yaw=Math.toRadians(p.getYaw()),progress=age/duration.get();
        int count=VisualQuality.particles(18);
        for(int i=0;i<count;i++){
            double t=i/(double)Math.max(1,count-1);
            double a=(t-.5)*2.5 + (progress-.5)*.5;
            var q=CosmeticParticleUtil.local(p.getX(),p.getY(),p.getZ(),yaw,Math.sin(a)*width.get(),1.1+(t-.5)*.4,Math.cos(a)*width.get());
            if(style.get()==Style.CRESCENT)CosmeticParticleUtil.dust(client,color,t,.55f,q.x(),q.y(),q.z());
            else client.particleManager.addParticle(style.get()==Style.FIRE?net.minecraft.particle.ParticleTypes.SMALL_FLAME:net.minecraft.particle.ParticleTypes.SNOWFLAKE,q.x(),q.y(),q.z(),0,.004,0);
        }
    }
    private void reset(){age=99;previous=0;world=null;}
    @Override protected void onDisable(){reset();}
}
