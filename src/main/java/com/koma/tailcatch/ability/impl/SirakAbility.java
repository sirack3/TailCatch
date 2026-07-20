package com.koma.tailcatch.ability.impl;

import com.koma.tailcatch.ability.Ability;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

/**
 * 시락 고유 능력.
 * 패시브: 신속 I 상시 + 주변 10칸 늑대 자동 길들이기 (5초 주기)
 * 액티브: 10초간 힘 I + 재생 I
 */
public class SirakAbility implements Ability {

    private final JavaPlugin plugin;
    // 패시브 늑대 길들이기 태스크 ID (플레이어별)
    private final java.util.Map<java.util.UUID, Integer> wolfTaskIds = new java.util.HashMap<>();

    public SirakAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "시락";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.AQUA + "시락의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "신속 I 상시 + 주변 10칸 늑대 자동 길들이기";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 힘 I + 재생 I";
    }

    @Override
    public void applyPassive(Player player) {
        // 신속 I 영구 적용
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false, true));

        // 5초마다 주변 10칸 늑대 자동 길들이기
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) return;
            tameNearbyWolves(player);
        }, 20L, 100L); // 5초 주기

        wolfTaskIds.put(player.getUniqueId(), taskId);
    }

    @Override
    public void removePassive(Player player) {
        // 신속 효과 제거
        player.removePotionEffect(PotionEffectType.SPEED);

        // 늑대 길들이기 태스크 정지
        Integer taskId = wolfTaskIds.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public void useActive(Player player) {
        // 10초간 힘 I + 재생 I
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, true, true));

        // 황금빛 파티클 효과
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 80, 0.5, 0.7, 0.5, 0.3);

        // 효과음
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.0f);
    }

    /** 주변 10칸 이내 늑대를 플레이어의 것으로 길들이기 */
    private void tameNearbyWolves(Player player) {
        // 이미 주인이 소유한 늑대 수 계산 (월드 전체)
        int ownedCount = 0;
        for (Entity e : player.getWorld().getEntities()) {
            if (e instanceof Wolf wolf) {
                if (wolf.isTamed() && player.equals(wolf.getOwner())) {
                    ownedCount++;
                }
            }
        }

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
                player.getLocation(), 10, 10, 10,
                e -> e instanceof Wolf
        );

        for (Entity e : nearby) {
            Wolf wolf = (Wolf) e;
            if (!wolf.isTamed()) {
                if (ownedCount >= 6) {
                    break; // 최대 6마리 제한
                }
                wolf.setTamed(true);
                wolf.setOwner(player);
                wolf.setTarget(null); // 공격 중지
                // 길들일 때 파티클 효과
                wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                ownedCount++;
            }
        }
    }
}
