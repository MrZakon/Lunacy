package net.lunacy.visuals.module.impl.hud;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.hud.*;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
public final class DamageIndicatorsModule extends HudModule {
 private final ColorSetting color=add(new ColorSetting("color","Цвет","Цвет урона",new ColorValue(0xFFFFE36E,0xFFFF627E,false,.5)));
 private final Map<Integer,Float> health=new HashMap<>();private final ArrayDeque<Hit> hits=new ArrayDeque<>();private Object world;
 public DamageIndicatorsModule(){super("damage_indicators","Индикаторы урона","Последний нанесённый урон с анимацией",new HudPosition(HudAnchor.CENTER,0,-65,1));}
 @Subscribe private void tick(TickEvent.Client event){var c=event.client();if(c.level==null){health.clear();hits.clear();world=null;return;}if(world!=c.level){health.clear();hits.clear();world=c.level;}for(var e:c.level.entitiesForRendering())if(e instanceof LivingEntity living&&e!=c.player){float now=living.getHealth();Float old=health.put(e.getId(),now);if(old!=null&&now<old-.04)hits.addFirst(new Hit(old-now,System.nanoTime()));}while(hits.size()>4)hits.removeLast();hits.removeIf(h->(System.nanoTime()-h.at)>1_300_000_000L);}
 @Override protected double width(){return 92;}@Override protected double height(){return Math.max(18,hits.size()*15+6);}@Override protected boolean shouldRender(){return !hits.isEmpty();}
 @Override protected void render(GuiGraphicsExtractor g,double x,double y,double scale,float delta){long now=System.nanoTime();int i=0;for(var hit:hits){double age=(now-hit.at)/1e9;int a=(int)(255*Math.max(0,1-age/1.3));String text="-"+String.format(java.util.Locale.ROOT,"%.1f",hit.damage);double tw=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,text,10);UiRenderer.font(g,FontFace.GOLOS_SEMI,text,x+(width()-tw)/2,y+i++*15,10,(a<<24)|(color.get().colorAt(.5,System.currentTimeMillis())&0xFFFFFF));}}
 private record Hit(float damage,long at){}
}
