package com.koma.tailcatch.ability.impl;

import com.koma.tailcatch.ability.Ability;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

public class CheongwolAbility implements Ability {
    
    private final JavaPlugin plugin;

    public CheongwolAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "청월";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.AQUA + "청월(토끼)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "낙하 데미지 무효, 신속 I 상시 적용";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 주변 15칸 내 모든 적에게 발광 효과 부여";
    }

    @Override
    public void applyPassive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false, true));
        
        org.bukkit.attribute.AttributeInstance safeFall = player.getAttribute(org.bukkit.attribute.Attribute.SAFE_FALL_DISTANCE);
        if (safeFall != null) {
            safeFall.setBaseValue(1000.0);
        }
    }

    @Override
    public void removePassive(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        
        org.bukkit.attribute.AttributeInstance safeFall = player.getAttribute(org.bukkit.attribute.Attribute.SAFE_FALL_DISTANCE);
        if (safeFall != null) {
            safeFall.setBaseValue(3.0); // 바닐라 기본값
        }
    }

    @Override
    public void useActive(Player player) {
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 100, 3, 1, 3, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1.5f);

        com.koma.tailcatch.GameManager gm = com.koma.tailcatch.TailCatchPlugin.getPlugin(com.koma.tailcatch.TailCatchPlugin.class).getGameManager();
        com.koma.tailcatch.Team myTeam = gm.getTeamOf(player);

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(player.getLocation(), 15, 15, 15, e -> e instanceof Player && !e.equals(player));
        for (Entity e : nearby) {
            Player target = (Player) e;
            com.koma.tailcatch.Team targetTeam = gm.getTeamOf(target);
            if (myTeam != null && targetTeam != null && !myTeam.equals(targetTeam)) {
                // 10초 = 200틱
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                target.sendMessage(ChatColor.YELLOW + "청월의 능력으로 위치가 드러납니다!");
            }
        }
    }
}
