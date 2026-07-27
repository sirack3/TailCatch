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

public class YudaAbility implements Ability {
    
    private final JavaPlugin plugin;

    public YudaAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNickname() {
        return "유다";
    }

    @Override
    public String getAbilityName() {
        return ChatColor.AQUA + "유다(해파리)의 힘";
    }

    @Override
    public String getPassiveDescription() {
        return "상시 투명화(반투명), 호흡 III, 친수성(돌고래의 가호)";
    }

    @Override
    public String getActiveDescription() {
        return "5초간 주변 6칸 내 상대에게 독 I, 구속 II 부여";
    }

    @Override
    public void applyPassive(Player player) {
        // 투명화 적용 (모두에게 반투명하게 보임)
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 2, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, true, false, true));
    }

    @Override
    public void removePassive(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
    }

    @Override
    public void useActive(Player player) {
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1.0f, 1.0f);

        com.koma.tailcatch.GameManager gm = com.koma.tailcatch.TailCatchPlugin.getPlugin(com.koma.tailcatch.TailCatchPlugin.class).getGameManager();
        com.koma.tailcatch.Team myTeam = gm.getTeamOf(player);

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(player.getLocation(), 6, 6, 6, e -> e instanceof Player && !e.equals(player));
        for (Entity e : nearby) {
            Player target = (Player) e;
            com.koma.tailcatch.Team targetTeam = gm.getTeamOf(target);
            if (myTeam != null && targetTeam != null && !myTeam.equals(targetTeam)) {
                // 5초 = 100틱
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1)); // 구속 2 (amp 1)
                target.sendMessage(ChatColor.DARK_GREEN + "유다의 독에 감염되었습니다!");
            }
        }
    }
}
