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

public class KoiAbility implements Ability {
    
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> taskIds = new HashMap<>();

    public KoiAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "레코이";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.GREEN + "레코이(뱀)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "물에 닿을 시 구속 I, 화염저항 상시, 공격 시 20% 확률로 독 I(2초)";
    }

    @Override
    public String getActiveDescription() {
        return "조준한 대상(또는 가장 가까운 적)을 4초간 완벽히 속박";
    }

    @Override
    public void applyPassive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false, true));

        // 1초마다 물에 닿아있는지 체크
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) return;
            org.bukkit.block.Block block = player.getLocation().getBlock();
            if (block.getType() == org.bukkit.Material.WATER) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, false));
            }
        }, 0L, 20L);
        taskIds.put(player.getUniqueId(), taskId);
    }

    @Override
    public void removePassive(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        Integer taskId = taskIds.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @Override
    public void useActive(Player player) {
        // 레이트레이싱으로 15칸 내의 타겟 탐색
        Player target = null;
        org.bukkit.entity.Entity hitEntity = null;
        
        try {
            org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), 
                    player.getEyeLocation().getDirection(), 
                    15.0, 
                    e -> e instanceof Player && !e.equals(player)
            );
            if (result != null && result.getHitEntity() != null) {
                hitEntity = result.getHitEntity();
            }
        } catch (Exception ignored) {}

        com.koma.tailcatch.GameManager gm = com.koma.tailcatch.TailCatchPlugin.getPlugin(com.koma.tailcatch.TailCatchPlugin.class).getGameManager();
        com.koma.tailcatch.Team myTeam = gm.getTeamOf(player);

        if (hitEntity instanceof Player p) {
            com.koma.tailcatch.Team tTeam = gm.getTeamOf(p);
            if (tTeam != null && myTeam != null && !tTeam.equals(myTeam)) {
                target = p;
            }
        }

        // 레이트레이스로 못찾았으면 가장 가까운 적 찾기 (반경 10칸)
        if (target == null) {
            double closest = 10.0;
            for (org.bukkit.entity.Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 10, 10, 10)) {
                if (e instanceof Player p && !p.equals(player) && !gm.isFrozen(p)) {
                    com.koma.tailcatch.Team tTeam = gm.getTeamOf(p);
                    if (tTeam != null && myTeam != null && !tTeam.equals(myTeam)) {
                        double d = p.getLocation().distance(player.getLocation());
                        if (d < closest) {
                            closest = d;
                            target = p;
                        }
                    }
                }
            }
        }

        if (target != null) {
            // 4초 = 80틱
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 255));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128));
            
            target.sendMessage(ChatColor.DARK_RED + "레코이의 뱀그물에 걸려 4초간 이동할 수 없습니다!");
            player.sendMessage(ChatColor.GREEN + target.getName() + "님을 4초간 속박했습니다!");
            
            target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            target.playSound(target.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.5f);
        } else {
            player.sendMessage(ChatColor.RED + "주변에 속박할 적이 없습니다.");
            // 에메랄드를 돌려줄 수도 있지만 그냥 낭비됨.
        }
    }
}
