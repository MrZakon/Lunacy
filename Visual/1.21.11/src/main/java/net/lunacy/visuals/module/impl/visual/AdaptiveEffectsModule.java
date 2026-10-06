package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.render.AdaptiveBudget;
public final class AdaptiveEffectsModule extends Module {
    private final DoubleSetting target=add(new DoubleSetting("target_fps","Целевой FPS","Плотность частиц снижается при просадках",90,30,240,5));
    private final AdaptiveBudget budget=new AdaptiveBudget();
    private int ticks;
    public AdaptiveEffectsModule(){super("adaptive_effects","Автоплотность эффектов","Плавное снижение частиц и медленное восстановление",ModuleCategory.RENDER_FX);}
    @Subscribe private void tick(TickEvent.Client event){
        if(event.client().player==null){budget.reset();return;}
        if(++ticks%10==0)budget.sample(event.client().getCurrentFps(),target.get());
    }
    public double factor(){return isEnabled()?budget.factor():1;}
    @Override public String displayValue(){return Math.round(factor()*100)+"%";}
    @Override protected void onDisable(){budget.reset();ticks=0;}
}
