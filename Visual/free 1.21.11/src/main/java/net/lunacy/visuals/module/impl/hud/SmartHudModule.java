package net.lunacy.visuals.module.impl.hud;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
public final class SmartHudModule extends Module {
    private final DoubleSetting durability=add(new DoubleSetting("durability","Прочность %","Порог предупреждения брони",20,5,80,5));
    private final DoubleSetting seconds=add(new DoubleSetting("seconds","Осталось секунд","Предупреждение об окончании эффекта",10,3,60,1));
    private final BooleanSetting hide=add(new BooleanSetting("hide","Скрывать спокойные","Убирать виджеты без предупреждений",true));
    public SmartHudModule(){super("smart_hud","Умный HUD","Прочность брони и заканчивающиеся эффекты привлекают внимание",ModuleCategory.HUD);}
    public boolean urgent(String id){
        var p=MinecraftClient.getInstance().player;if(p==null)return false;
        if(id.equals("armor_hud")){
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
                var item=p.getEquippedStack(slot);
                if(item.isDamageable() && 100.0*(item.getMaxDamage()-item.getDamage())/Math.max(1,item.getMaxDamage())<=durability.get())return true;
            }
        }
        if(id.equals("potion_hud"))for(var effect:p.getStatusEffects())if(!effect.isInfinite() && effect.getDuration()<=seconds.get()*20)return true;
        return false;
    }
    public boolean visible(String id){return !isEnabled() || !hide.get() || !(id.equals("armor_hud") || id.equals("potion_hud")) || urgent(id);}
}
