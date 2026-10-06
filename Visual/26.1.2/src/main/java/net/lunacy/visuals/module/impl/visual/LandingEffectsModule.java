package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.core.particles.ParticleTypes;
public final class LandingEffectsModule extends Module {
    public enum Style { RING, ICE, DUST }
    private final EnumSetting<Style> style=add(new EnumSetting<>("style","Эффект","Кольцо, лёд или пыль",Style.RING,Style.class));
    private final ColorSetting color=add(new ColorSetting("color","Цвет","Градиент кольца",new ColorValue(0xFF8BEAFF,0xFFAD8AFF,false,.6)));
    private final DoubleSetting minimum=add(new DoubleSetting("minimum","Высота падения","Минимальная высота срабатывания",1.5,.5,6,.25));
    private final DoubleSetting maximum=add(new DoubleSetting("maximum","Макс. радиус","Ограничение размера",2.5,.5,5,.25));
    private Object world;
    private boolean grounded=true;
    private double peak,x,y,z,radius;
    private int age=99;
    public LandingEffectsModule(){ super("landing_fx","Приземление","Кольцо, пыль или ледяные искры при приземлении",ModuleCategory.RENDER_FX); }
    @Subscribe private void tick(TickEvent.Client event){
        var client=event.client();
        if(client.player==null || client.level==null){world=null;age=99;return;}
        var p=client.player;
        if(world!=client.level){world=client.level;grounded=p.onGround();peak=p.getY();age=99;}
        boolean now=p.onGround();
        if(!now) peak=Math.max(peak,p.getY());
        if(now && !grounded && peak-p.getY()>=minimum.get()){
            radius=Math.min(maximum.get(),.5+Math.sqrt(peak-p.getY())*.5);
            x=p.getX();y=p.getY()+.04;z=p.getZ();age=0;
        }
        if(now)peak=p.getY();grounded=now;
        if(age++<9){
            int count=VisualQuality.particles(32);
            for(int i=0;i<count;i++){
                double a=i*Math.PI*2/count,r=radius*age/9;
                double px=x+Math.cos(a)*r,pz=z+Math.sin(a)*r;
                if(style.get()==Style.RING)CosmeticParticleUtil.dust(client,color,i/(double)count,.6f,px,y,pz);
                else if(age<4)client.particleEngine.createParticle(style.get()==Style.ICE?ParticleTypes.SNOWFLAKE:ParticleTypes.POOF,px,y,pz,Math.cos(a)*.035,.025,Math.sin(a)*.035);
            }
        }
    }
    @Override protected void onDisable(){world=null;age=99;}
}
