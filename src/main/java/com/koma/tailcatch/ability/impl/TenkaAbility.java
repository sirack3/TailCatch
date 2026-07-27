package com.koma.tailcatch.ability.impl;

import com.koma.tailcatch.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TenkaAbility implements Ability {
    
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> taskIds = new HashMap<>();

    public TenkaAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "텐카";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.GOLD + "텐카(여우)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "낙하 대미지 50% 감소, 밤일 때 신속 II/힘 I, 음식 섭취 시 재생 I(4초)";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 흡수 I, 재생 II 부여";
    }

    @Override
    public void applyPassive(Player player) {
        // 밤낮 확인 태스크 (1초마다)
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) return;
            long time = player.getWorld().getTime();
            // 밤 시간대 (13000 ~ 23000)
            if (time >= 13000 && time <= 23000) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, true, false, false));
            }
        }, 0L, 20L);
        taskIds.put(player.getUniqueId(), taskId);
    }

    @Override
    public void removePassive(Player player) {
        Integer taskId = taskIds.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public void useActive(Player player) {
        // 10초 = 200틱
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
        
        player.playSound(player.getLocation(), Sound.ENTITY_FOX_SCREECH, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.0);
    }
}
