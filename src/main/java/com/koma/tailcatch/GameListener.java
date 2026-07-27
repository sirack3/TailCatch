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
    private final java.util.Map<java.util.UUID, java.util.UUID> lastAttackerMap = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastAttackerTimeMap = new java.util.HashMap<>();

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
                } else if (damager instanceof org.bukkit.entity.Tameable tameable) {
                    if (tameable.isTamed() && tameable.getOwner() instanceof Player owner) {
                        attacker = owner;
                    }
                }

                // 피해자가 정지 상태인데, 플레이어가 아닌 엔티티(몹 등)의 공격일 경우 무조건 무효화
                if (gameManager.isFrozen(victim) && attacker == null) {
                    event.setCancelled(true);
                    return;
                }

                if (attacker != null) {
                    lastAttackerMap.put(victim.getUniqueId(), attacker.getUniqueId());
                    lastAttackerTimeMap.put(victim.getUniqueId(), System.currentTimeMillis());
                    
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

                    // 1.5 기절한(정지된) 플레이어에 대한 공격 제한
                    if (gameManager.isFrozen(victim)) {
                        boolean isHunter = false;
                        if (attackerTeam.getTargetTeam() != null && attackerTeam.getTargetTeam().equals(victimTeam)) {
                            isHunter = true;
                        }
                        
                        if (!isHunter) {
                            event.setCancelled(true);
                            if (damager instanceof Player) {
                                attacker.sendMessage(org.bukkit.ChatColor.RED + "기절한 타겟은 사냥꾼 팀만 공격할 수 있습니다!");
                            }
                            return;
                        }
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
                Player killer = null;

                if (event instanceof EntityDamageByEntityEvent entityEvent) {
                    org.bukkit.entity.Entity damager = entityEvent.getDamager();
                    if (damager instanceof Player) {
                        killer = (Player) damager;
                    } else if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player) {
                        killer = (Player) proj.getShooter();
                    } else if (damager instanceof org.bukkit.entity.Tameable tame && tame.isTamed() && tame.getOwner() instanceof Player) {
                        killer = (Player) tame.getOwner();
                    }
                }

                if (killer == null) {
                    Long lastTime = lastAttackerTimeMap.get(victim.getUniqueId());
                    if (lastTime != null && System.currentTimeMillis() - lastTime <= 10000) {
                        java.util.UUID killerId = lastAttackerMap.get(victim.getUniqueId());
                        if (killerId != null) {
                            killer = org.bukkit.Bukkit.getPlayer(killerId);
                        }
                    }
                }

                if (killer != null && killer.isOnline() && gameManager.canCatch(killer, victim)) {
                    caught = true;
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

                    gameManager.handleCatch(killer, victim);
                } else {
                    // 주인이든 노예든 타겟 외의 원인으로 죽을 경우 (자연사) 얼어붙게 함
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
                
                lastAttackerMap.remove(victim.getUniqueId());
                lastAttackerTimeMap.remove(victim.getUniqueId());
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

        boolean isRightClick = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

        if (!isRightClick) return;

        org.bukkit.Material hand = player.getInventory().getItemInMainHand().getType();

        // 노예는 다이아몬드(추적기) 사용 불가
        Team slaveTeamCheck = gameManager.getTeamOf(player);
        if (hand == org.bukkit.Material.DIAMOND) {
            if (slaveTeamCheck != null && slaveTeamCheck.getSlaves().contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "노예는 추적기를 사용할 수 없습니다!");
                return;
            }
            gameManager.useTracker(player);
        }

        // 에메랄드 우클릭 → 능력 액티브 발동
        if (hand == org.bukkit.Material.EMERALD) {
            event.setCancelled(true);
            gameManager.getAbilityManager().onEmeraldUse(player);
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
        if (gameManager.isGameRunning()) {
            gameManager.startDisconnectTimer(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (gameManager.isGameRunning()) {
            gameManager.cancelDisconnectTimer(event.getPlayer());
            // 이름 복원은 약간의 딜레이를 주어 NameTagManager의 TextDisplay 복원 후 처리
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                gameManager.getPlugin(), () -> {
                    if (event.getPlayer().isOnline()) {
                        gameManager.restorePlayerNames(event.getPlayer());
                    }
                }, 10L
            );
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
                // 노예가 인벤에서 다이아몬드를 다른 칸으로 옮기는 행위도 제한
                else if (type == org.bukkit.Material.DIAMOND) {
                    event.setCancelled(true);
                    player.sendMessage(org.bukkit.ChatColor.RED + "노예는 추적기를 사용할 수 없습니다!");
                }
            }
            // 일반 클릭으로 다이아몬드 집는 행위 제한
            else if (event.getCurrentItem() != null && event.getCurrentItem().getType() == org.bukkit.Material.DIAMOND) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "노예는 추적기를 사용할 수 없습니다!");
            }
        }
    }

    // onPlayerRespawn 삭제 (더 이상 실제 사망하지 않고 정지됨)
}
