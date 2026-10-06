package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import java.util.ArrayDeque;
public final class GhostAfterimagesModule extends Module {
    private final ColorSetting color=add(new ColorSetting("color","Цвет","Градиент призрачных силуэтов",new ColorValue(0xFF77E7A2,0xFFA66BFF,false,.65)));
    private final DoubleSetting copies=add(new DoubleSetting("copies","Копии","Число силуэтов",4,1,8,1));
    private final DoubleSetting spacing=add(new DoubleSetting("spacing","Интервал","Расстояние между отпечатками",.45,.15,1,.05));
    private final ArrayDeque<Pose> history=new ArrayDeque<>(); private Object world;
    public GhostAfterimagesModule(){super("ghost_afterimages","Призрачные силуэты","Растворяющиеся силуэты из частиц во время движения",ModuleCategory.COSMETICS);}
    @Subscribe private void tick(TickEvent.Client event){
        var c=event.client();if(c.player==null||c.world==null){reset();return;}if(world!=c.world){reset();world=c.world;}
        var p=c.player;var last=history.peekFirst();
        if(last==null||Math.hypot(last.x-p.getX(),last.z-p.getZ())>=spacing.get()){history.addFirst(new Pose(p.getX(),p.getY(),p.getZ(),Math.toRadians(p.getYaw())));while(history.size()>copies.get())history.removeLast();}
        int index=0;for(var pose:history){if(index++==0)continue;int points=VisualQuality.particles(9);for(int i=0;i<points;i++){
            double phase=i/(double)points;double side=(i%2==0?-.16:.16);double up=.15+(i%5)*.35;
            var q=CosmeticParticleUtil.local(pose.x,pose.y,pose.z,pose.yaw,side,up,0);
            CosmeticParticleUtil.dust(c,color,phase+index*.1,Math.max(.25f,.65f-index*.08f),q.x(),q.y(),q.z());
        }}
    }
    private void reset(){history.clear();world=null;} @Override protected void onDisable(){reset();}
    private record Pose(double x,double y,double z,double yaw){}
}
