package com.koma.tailcatch;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.attribute.Attribute;

public class GameListener implements Listener {
    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager.isGameRunning()) {
            event.setDeathMessage(null);
            
            Player player = event.getEntity();
            if (gameManager.isFrozen(player)) {
                gameManager.unfreezePlayer(player);
            }
            
            // 노예가 죽었을 때 아이템 잃지 않게
            Team team = gameManager.getTeamOf(player);
            if (team != null && team.getSlaves().contains(player.getUniqueId())) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameManager.isGameRunning()) return;

        if (gameManager.isFrozen(event.getPlayer())) {
            if (event.getFrom().getX() != event.getTo().getX() || 
                event.getFrom().getZ() != event.getTo().getZ() ||
                event.getFrom().getY() != event.getTo().getY()) {
                
                org.bukkit.Location newTo = event.getFrom().clone();
                newTo.setPitch(event.getTo().getPitch());
                newTo.setYaw(event.getTo().getYaw());
                event.setTo(newTo);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameManager.isGameRunning()) return;

        if (event.getEntity() instanceof Player victim) {
            
            if (event instanceof EntityDamageByEntityEvent entityEvent) {
                org.bukkit.entity.Entity damager = entityEvent.getDamager();
                Player attacker = null;
                
                if (damager instanceof Player) {
                    attacker = (Player) damager;
                } else if (damager instanceof org.bukkit.entity.Projectile) {
                    org.bukkit.projectiles.ProjectileSource source = ((org.bukkit.entity.Projectile) damager).getShooter();
                    if (source instanceof Player) {
                        attacker = (Player) source;
                    }
                }

                // 피해자가 정지 상태인데, 플레이어가 아닌 엔티티(몹 등)의 공격일 경우 무조건 무효화
                if (gameManager.isFrozen(victim) && attacker == null) {
                    event.setCancelled(true);
                    return;
                }

                if (attacker != null) {
                    Team attackerTeam = gameManager.getTeamOf(attacker);
                    Team victimTeam = gameManager.getTeamOf(victim);

                if (attackerTeam != null && victimTeam != null) {
                    // 1. 아군 공격(주인-노예, 노예-노예) 방지
                    if (attackerTeam.equals(victimTeam)) {
                        event.setCancelled(true);
                        if (attackerTeam.getSlaves().contains(attacker.getUniqueId()) && attackerTeam.getMasterId().equals(victim.getUniqueId())) {
                            attacker.sendMessage(org.bukkit.ChatColor.RED + "주인님을 공격할 수 없습니다!");
                        }
                        return;
                    }

                    // 2. 타겟 관계 확인 (노예는 예외)
                    boolean isAttackerSlave = attackerTeam.getSlaves().contains(attacker.getUniqueId());
                    boolean isVictimSlave = victimTeam.getSlaves().contains(victim.getUniqueId());

                    // 둘 다 노예가 아닐 때 (주인 대 주인)
                    if (!isAttackerSlave && !isVictimSlave) {
                        boolean canAttack = false;
                        
                        // 공격자의 타겟이 피해자이거나
                        if (attackerTeam.getTargetTeam() != null && attackerTeam.getTargetTeam().equals(victimTeam)) {
                            canAttack = true;
                        }
                        // 피해자의 타겟이 공격자이거나 (반격)
                        if (victimTeam.getTargetTeam() != null && victimTeam.getTargetTeam().equals(attackerTeam)) {
                            canAttack = true;
                        }

                        if (!canAttack) {
                            event.setCancelled(true);
                            if (damager instanceof Player) {
                                attacker.sendMessage(org.bukkit.ChatColor.RED + "당신의 타겟이 아닙니다!");
                            }
                            return;
                        }
                    }
                }
            } // end if (attacker != null)
        } // end if (event instanceof EntityDamageByEntityEvent)

        // 정지 상태일 때는 허용된 플레이어 공격(PvP) 외의 모든 데미지(낙하, 불 등) 무효화
            if (gameManager.isFrozen(victim)) {
                if (!(event instanceof EntityDamageByEntityEvent)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (victim.getHealth() - event.getFinalDamage() <= 0) {
                boolean caught = false;
                if (event instanceof EntityDamageByEntityEvent entityEvent) {
                    if (entityEvent.getDamager() instanceof Player killer) {
                        if (gameManager.canCatch(killer, victim)) {
                            caught = true;
                        }
                    }
                }

                if (caught) {
                    event.setCancelled(true);
                    double maxHealth = 20.0;
                    if (victim.getAttribute(Attribute.MAX_HEALTH) != null) {
                        maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                    }
                    victim.setHealth(maxHealth);
                    victim.setFoodLevel(20);
                    victim.setFireTicks(0);

                    Player killer = (Player) ((EntityDamageByEntityEvent) event).getDamager();
                    gameManager.handleCatch(killer, victim);
                } else {
                    Team team = gameManager.getTeamOf(victim);
                    if (team != null && team.getSlaves().contains(victim.getUniqueId())) {
                        // 노예가 타겟 외의 원인으로 죽을 경우 (자연사)
                        // 이벤트를 취소하지 않고 실제로 죽게 둠 (리턴)
                        return;
                    } else {
                        // 주인이 죽을 경우 얼어붙게 함
                        event.setCancelled(true);
                        double maxHealth = 20.0;
                        if (victim.getAttribute(Attribute.MAX_HEALTH) != null) {
                            maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
                        }
                        victim.setHealth(maxHealth);
                        victim.setFoodLevel(20);
                        victim.setFireTicks(0);
                        gameManager.freezePlayer(victim);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!gameManager.isGameRunning()) return;

        Player player = event.getPlayer();

        if (gameManager.isFrozen(player)) {
            event.setCancelled(true);
            return;
        }

        // 다이아몬드 우클릭 → 타겟 추적
        if ((event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
             event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) &&
            player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.DIAMOND) {
            gameManager.useTracker(player);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (gameManager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (gameManager.isFrozen(event.getPlayer())) {
            gameManager.unfreezePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Team team = gameManager.getTeamOf(player);
        if (team != null && team.getSlaves().contains(player.getUniqueId())) {
            // 방어구 슬롯 클릭 제한
            if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "노예는 갑옷을 벗을 수 없습니다!");
            }
            // 인벤토리에서 쉬프트 클릭으로 갑옷을 입고 벗는 행위 제한
            else if (event.isShiftClick() && event.getCurrentItem() != null) {
                org.bukkit.Material type = event.getCurrentItem().getType();
                if (type.name().contains("LEATHER_")) {
                    event.setCancelled(true);
                    player.sendMessage(org.bukkit.ChatColor.RED + "노예는 갑옷을 변경할 수 없습니다!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!gameManager.isGameRunning()) return;
        
        Player player = event.getPlayer();
        Team team = gameManager.getTeamOf(player);
        if (team != null && team.getSlaves().contains(player.getUniqueId())) {
            org.bukkit.entity.Player master = org.bukkit.Bukkit.getPlayer(team.getMasterId());
            if (master != null && master.isOnline()) {
                event.setRespawnLocation(master.getLocation());
                
                org.bukkit.Bukkit.getScheduler().runTaskLater(TailCatchPlugin.getPlugin(TailCatchPlugin.class), () -> {
                    player.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "주인님의 곁으로 부활했습니다!");
                }, 5L);
            }
        }
    }
}
