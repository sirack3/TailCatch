package com.koma.tailcatch.ability.impl;

import com.koma.tailcatch.ability.Ability;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class HwawolAbility implements Ability {
    
    private final JavaPlugin plugin;

    public HwawolAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "화월";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.GRAY + "화월(코알라)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "저항 I, 밀치기 저항 상시 적용";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 구속 I, 저항 III 부여";
    }

    @Override
    public void applyPassive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, true, false, true));
        
        AttributeInstance kbResist = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.setBaseValue(1.0); // 100% 넉백 무시
        }
    }

    @Override
    public void removePassive(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        
        AttributeInstance kbResist = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.setBaseValue(0.0);
        }
    }

    @Override
    public void useActive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2)); // 저항 3
        
        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0);
    }
}
