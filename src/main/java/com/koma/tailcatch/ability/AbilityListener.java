package com.koma.tailcatch.ability;

import com.koma.tailcatch.GameManager;
import com.koma.tailcatch.ability.impl.SeoulAbility;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class AbilityListener implements Listener {
    
    private final AbilityManager abilityManager;
    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final Random random = new Random();

    public AbilityListener(JavaPlugin plugin, AbilityManager abilityManager, GameManager gameManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        
        Ability ability = abilityManager.getPlayerAbility(player.getUniqueId());
        if (ability == null || !ability.getNickname().equals("서울")) return;

        // 비행 시작 항상 차단
        event.setCancelled(true);

        // 땅에 있을 때는 무시 (바닐라 점프 정상 발동)
        if (player.isOnGround()) return;

        // 이 시점에 쿨타임 중이면 AllowFlight=false라 이벤트 자체가 안 오므로
        // 여기 도달했다면 무조건 쿨타임 끝난 상태 → 더블점프 실행
        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        org.bukkit.util.Vector horizontal = new org.bukkit.util.Vector(dir.getX(), 0, dir.getZ());
        if (horizontal.lengthSquared() > 0.0001) {
            horizontal.normalize();
        }
        player.setVelocity(horizontal.multiply(0.7).setY(0.5));
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
        
        // 쿨타임 시작 (AllowFlight=false → 태스크가 10초 후 자동으로 true 복구)
        SeoulAbility.doubleJumpCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.setAllowFlight(false);
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Ability ability = abilityManager.getPlayerAbility(player.getUniqueId());
            if (ability != null) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    if (ability.getNickname().equals("청월")) {
                        event.setCancelled(true); // 낙하 데미지 무효
                    } else if (ability.getNickname().equals("텐카")) {
                        event.setDamage(event.getDamage() * 0.5); // 낙하 데미지 50% 감소
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Ability ability = abilityManager.getPlayerAbility(player.getUniqueId());
        if (ability != null && ability.getNickname().equals("텐카")) {
            if (event.getItem().getType().isEdible()) {
                // 음식 섭취 시 재생 I 4초
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return; // 이벤트가 이미 취소되었다면 무시 (아군 공격 등)
        
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
            Ability ability = abilityManager.getPlayerAbility(attacker.getUniqueId());
            if (ability != null && ability.getNickname().equals("레코이")) {
                // 공격 시 20% 확률로 독 I (2초)
                if (random.nextDouble() < 0.20) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                    attacker.sendMessage(ChatColor.DARK_GREEN + "레코이의 독을 주입했습니다!");
                }
            }
        }
    }
}
