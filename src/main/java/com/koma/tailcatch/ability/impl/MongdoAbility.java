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

public class MongdoAbility implements Ability {
    
    private final JavaPlugin plugin;

    public MongdoAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "몽도";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.AQUA + "몽도(물개)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "돌고래의 가호, 호흡 III 상시 적용";
    }

    @Override
    public String getActiveDescription() {
        return "10초간 주변 10칸 내 상대에게 나약함 II, 구속 I 부여";
    }

    @Override
    public void applyPassive(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 2, true, false, true));
    }

    @Override
    public void removePassive(Player player) {
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
    }

    @Override
    public void useActive(Player player) {
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 100, 2, 1, 2, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1.0f, 1.0f);

        com.koma.tailcatch.GameManager gm = com.koma.tailcatch.TailCatchPlugin.getPlugin(com.koma.tailcatch.TailCatchPlugin.class).getGameManager();
        com.koma.tailcatch.Team myTeam = gm.getTeamOf(player);

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(player.getLocation(), 10, 10, 10, e -> e instanceof Player && !e.equals(player));
        for (Entity e : nearby) {
            Player target = (Player) e;
            com.koma.tailcatch.Team targetTeam = gm.getTeamOf(target);
            // 아군이 아닐 경우 부여 (타겟이든 아니든 상대방이면 모두 적용)
            if (myTeam != null && targetTeam != null && !myTeam.equals(targetTeam)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0));
                target.sendMessage(ChatColor.RED + "몽도의 능력으로 인해 약해집니다!");
            }
        }
    }
}
