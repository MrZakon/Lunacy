package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
public final class DeathEffectsModule extends Module {
 private static DeathEffectsModule active;
 public enum EffectType{LIGHTNING,SOUL_BURST,NEON_SHATTER}
 private final EnumSetting<EffectType> effect=add(new EnumSetting<>("effect","Эффект смерти","Молния, душа или неоновый взрыв",EffectType.LIGHTNING,EffectType.class));
 private final ColorSetting color=add(new ColorSetting("color","Цвет","Цвет неонового эффекта",new ColorValue(0xFF77E7A2,0xFFA66BFF,false,.7)));
 private final Map<Integer,Boolean> alive=new HashMap<>();private final Set<Integer> triggered=new HashSet<>();private Object world;
 public DeathEffectsModule(){super("death_effects","Эффекты смерти","Локальный эффект при смерти видимой сущности",ModuleCategory.RENDER_FX);active=this;}
 @Subscribe private void tick(TickEvent.Client event){var c=event.client();if(c.level==null){alive.clear();triggered.clear();world=null;return;}if(world!=c.level){alive.clear();triggered.clear();world=c.level;}for(var entity:c.level.entitiesForRendering())if(entity instanceof LivingEntity living&&entity!=c.player){boolean now=!living.isDeadOrDying();if(now){alive.put(entity.getId(),true);triggered.remove(entity.getId());}else if(alive.put(entity.getId(),false)==Boolean.TRUE&&triggered.add(entity.getId()))burst(c,living);}}
 public static void trigger(LivingEntity entity){var module=active;var client=Minecraft.getInstance();if(module!=null&&module.isEnabled()&&entity!=client.player&&client.level!=null&&module.triggered.add(entity.getId()))module.burst(client,entity);}
 private void burst(net.minecraft.client.Minecraft c,LivingEntity e){int count=VisualQuality.particles(140);for(int i=0;i<count;i++){double a=i*Math.PI*2/count;double y=(i%9)/8.0*e.getBbHeight();if(effect.get()==EffectType.NEON_SHATTER)CosmeticParticleUtil.dust(c,color,i/(double)count,.75f,e.getX()+Math.cos(a)*.45,e.getY()+y,e.getZ()+Math.sin(a)*.45);else c.particleEngine.createParticle(effect.get()==EffectType.LIGHTNING?ParticleTypes.ELECTRIC_SPARK:ParticleTypes.SOUL_FIRE_FLAME,e.getX(),e.getY()+y,e.getZ(),Math.cos(a)*.09,.04+Math.random()*.14,Math.sin(a)*.09);}}
 @Override public String displayValue(){return effect.get().name();}@Override protected void onDisable(){alive.clear();triggered.clear();world=null;if(active==this)active=null;}
}
