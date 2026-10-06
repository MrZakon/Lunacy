package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
public final class ProjectilePredictionModule extends Module {
 private final BooleanSetting pearl=add(new BooleanSetting("pearl","Жемчуг","Траектория жемчуга",true));private final BooleanSetting bow=add(new BooleanSetting("bow","Луки","Траектория стрел",true));private final BooleanSetting potions=add(new BooleanSetting("potions","Зелья","Траектория зелий",true));private final ColorSetting color=add(new ColorSetting("color","Цвет дуги","Градиент траектории",new ColorValue(0xFF4FFFD8,0xFF9E5CFF,false,.75)));
 private int tick;public ProjectilePredictionModule(){super("projectile_prediction","Предсказание траектории","Дуга полёта жемчуга, стрел и метательных предметов",ModuleCategory.RENDER_FX);}
 @Subscribe private void tick(TickEvent.Client event){var c=event.client();if(c.player==null||c.level==null||++tick%VisualQuality.cadence(3)!=0)return;ItemStack stack=c.player.getMainHandItem();if(!supported(stack.getItem()))stack=c.player.getOffhandItem();if(!supported(stack.getItem()))return;Item item=stack.getItem();double speed=item instanceof BowItem||item instanceof CrossbowItem?2.7:item instanceof SplashPotionItem||item instanceof LingeringPotionItem?.55:1.5;double gravity=item instanceof BowItem||item instanceof CrossbowItem||item instanceof SplashPotionItem||item instanceof LingeringPotionItem?.05:.03;Vec3 pos=c.player.getEyePosition(),vel=c.player.getLookAngle().scale(speed).add(c.player.getDeltaMovement());int points=VisualQuality.particles(28);for(int i=0;i<points;i++){pos=pos.add(vel);vel=new Vec3(vel.x*.99,(vel.y-gravity)*.99,vel.z*.99);if(i%2==0)CosmeticParticleUtil.dust(c,color,i/(double)points,.45f,pos.x,pos.y,pos.z);if(pos.y<c.player.getY()-5)break;}}
 private boolean supported(Item i){return i instanceof EnderpearlItem&&pearl.get()||(i instanceof BowItem||i instanceof CrossbowItem)&&bow.get()||(i instanceof SplashPotionItem||i instanceof LingeringPotionItem)&&potions.get()||i instanceof SnowballItem||i instanceof EggItem||i instanceof WindChargeItem;}
}
