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

public class SeoulAbility implements Ability {
    
    private final JavaPlugin plugin;
    public static final Map<UUID, Long> doubleJumpCooldowns = new HashMap<>();
    private final Map<UUID, Integer> taskIds = new HashMap<>();

    public SeoulAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "서울";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.GREEN + "서울(도마뱀)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "두 칸 점프 (공중에서 점프, 10초 쿨타임)";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 완벽한 투명화(장비 포함 숨김) + 이속 II";
    }

    @Override
    public void applyPassive(Player player) {
        // 쿨타임 중이면 AllowFlight=false (이벤트 자체 차단), 끝나면 true 복구
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR ||
                player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

            long lastJump = doubleJumpCooldowns.getOrDefault(player.getUniqueId(), 0L);
            boolean cooldownOver = System.currentTimeMillis() - lastJump >= 10000;

            if (cooldownOver) {
                // 쿨타임 끝남: AllowFlight 허용
                if (!player.getAllowFlight()) player.setAllowFlight(true);
            } else {
                // 쿨타임 중: AllowFlight 차단 (이벤트 자체 발생 안함)
                if (player.getAllowFlight()) player.setAllowFlight(false);
            }
        }, 0L, 5L); // 0.25초마다 체크
        taskIds.put(player.getUniqueId(), taskId);
    }

    @Override
    public void removePassive(Player player) {
        Integer taskId = taskIds.remove(player.getUniqueId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        doubleJumpCooldowns.remove(player.getUniqueId());

        // 만약 투명화 중이었다면 복구
        org.bukkit.entity.TextDisplay display = com.koma.tailcatch.NameTagManager.getTextDisplay(player.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                p.showPlayer(plugin, player);
                if (display != null) p.showEntity(plugin, display);
            }
        }
    }

    @Override
    public void useActive(Player player) {
        // 10초간 완벽한 투명화 + 신속 2
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, true, false, true));
        
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);

        org.bukkit.entity.TextDisplay display = com.koma.tailcatch.NameTagManager.getTextDisplay(player.getUniqueId());

        // 다른 모든 플레이어에게서 이 플레이어와 텍스트 디스플레이를 숨김
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) {
                p.hidePlayer(plugin, player);
                if (display != null) p.hideEntity(plugin, display);
            }
        }
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                org.bukkit.entity.TextDisplay currentDisplay = com.koma.tailcatch.NameTagManager.getTextDisplay(player.getUniqueId());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player)) {
                        p.showPlayer(plugin, player);
                        if (currentDisplay != null) p.showEntity(plugin, currentDisplay);
                    }
                }
                player.sendMessage(ChatColor.YELLOW + "투명화가 해제되었습니다.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            }
        }, 200L); // 10초
    }
}
