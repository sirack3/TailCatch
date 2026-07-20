package com.koma.tailcatch;

import com.koma.tailcatch.ability.AbilityManager;
import com.koma.tailcatch.ability.impl.SirakAbility;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class GameManager {
    private final JavaPlugin plugin;
    private boolean isGameRunning = false;
    private boolean isTestMode = false;
    private final Map<UUID, Team> playerTeamMap = new HashMap<>();
    private final List<Team> activeTeams = new ArrayList<>();
    private final Map<UUID, Long> frozenPlayers = new HashMap<>();
    private final Map<UUID, ArmorStand> freezeHolograms = new HashMap<>();
    private int distanceTask = -1;
    private int freezeTask = -1;
    private int heartbeatTask = -1;
    private double worldBorderSize = 1000.0;
    
    // 원래 닉네임 백업용 맵
    private final Map<UUID, String> originalDisplayNames = new HashMap<>();
    private final Map<UUID, String> originalListNames = new HashMap<>();
    
    // 퇴장 타이머
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> disconnectTimers = new HashMap<>();

    // 능력 시스템
    private final AbilityManager abilityManager;

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.abilityManager = new AbilityManager(plugin);
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.SirakAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.MongdoAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.YudaAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.SeoulAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.TenkaAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.HwawolAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.KoiAbility(plugin));
        this.abilityManager.register(new com.koma.tailcatch.ability.impl.CheongwolAbility(plugin));
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public boolean isGameRunning() {
        return isGameRunning;
    }

    public double getWorldBorderSize() {
        return worldBorderSize;
    }

    public void setWorldBorderSize(double worldBorderSize) {
        this.worldBorderSize = worldBorderSize;
    }

    public boolean isFrozen(Player p) {
        return frozenPlayers.containsKey(p.getUniqueId());
    }

    public void freezePlayer(Player p) {
        frozenPlayers.put(p.getUniqueId(), System.currentTimeMillis() + 30000);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        p.sendTitle(ChatColor.RED + "자연사!", ChatColor.GRAY + "30초 동안 움직일 수 없습니다.", 10, 70, 20);

        // 머리 위 타이머 홀로그램 생성
        ArmorStand stand = p.getWorld().spawn(p.getLocation().add(0, 2.2, 0), ArmorStand.class, s -> {
            s.setVisible(false);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            s.setGravity(false);
            s.setCustomName(ChatColor.RED + "정지: 30초");
        });
        freezeHolograms.put(p.getUniqueId(), stand);
        if (p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)!=null) p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(0.0);
        if (p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!=null) p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).setBaseValue(0.0);
    }

    public void unfreezePlayer(Player p) {
        frozenPlayers.remove(p.getUniqueId());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
        
        if (p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE) != null) {
            p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getDefaultValue());
        }
        if (p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) != null) {
            p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).setBaseValue(p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getDefaultValue());
        }

        ArmorStand stand = freezeHolograms.remove(p.getUniqueId());
        if (stand != null) {
            stand.remove();
        }

        Team team = getTeamOf(p);
        if (team != null && team.getSlaves().contains(p.getUniqueId())) {
            Player master = Bukkit.getPlayer(team.getMasterId());
            if (master != null && master.isOnline()) {
                p.teleport(master.getLocation());
                p.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
                p.sendMessage(ChatColor.GREEN + "주인님의 곁으로 부활했습니다!");
            }
        }
    }

    public void startGame() {
        startGameInternal(false);
    }

    public void startTestGame() {
        startGameInternal(true);
    }

    private void startGameInternal(boolean testMode) {
        if (isGameRunning) return;

        this.isTestMode = testMode;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int minPlayers = testMode ? 1 : 2;
        if (players.size() < minPlayers) {
            Bukkit.broadcastMessage(ChatColor.RED + "최소 " + minPlayers + "명의 플레이어가 필요합니다!");
            return;
        }
        
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            w.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, false);
            w.setGameRule(org.bukkit.GameRule.REDUCED_DEBUG_INFO, true); // 좌표 표시 숨김
            
            w.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, false);
            w.setGameRule(org.bukkit.GameRule.LOG_ADMIN_COMMANDS, false);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule locator_bar false");
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, true);
            w.setGameRule(org.bukkit.GameRule.LOG_ADMIN_COMMANDS, true);
        }

        playerTeamMap.clear();
        activeTeams.clear();
        frozenPlayers.clear();
        for (ArmorStand stand : freezeHolograms.values()) {
            if (stand != null) stand.remove();
        }
        freezeHolograms.clear();

        // 닉네임 백업 초기화
        originalDisplayNames.clear();
        originalListNames.clear();

        for (Player p : players) {
            // 커스텀 닉네임(NameTagManager)이 있으면 그것을 백업, 없으면 원래 이름 백업
            String currentCustom = NameTagManager.getCustomName(p.getUniqueId());
            originalDisplayNames.put(p.getUniqueId(), currentCustom != null ? currentCustom : p.getName());
            originalListNames.put(p.getUniqueId(), p.getPlayerListName());
            
            // 인벤토리 초기화
            p.getInventory().clear();
            p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);

            // 경험치바(로케이트바) 초기화
            p.setLevel(0);
            p.setExp(0f);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
            }
            p.setHealth(20.0);
            Team t = new Team(p.getUniqueId());
            playerTeamMap.put(p.getUniqueId(), t);
            activeTeams.add(t);
            
            // 팀 배정 전에 순수 닉네임(팀 칭호 없는 상태)으로 능력 부여
            String rawName = originalDisplayNames.get(p.getUniqueId());
            String pureName = org.bukkit.ChatColor.stripColor(rawName).trim();
            abilityManager.onGameStart(p, pureName);
        }

        Collections.shuffle(activeTeams);

        // 팀 이름 및 색상 배정: A팀, B팀, C팀...
        for (int i = 0; i < activeTeams.size(); i++) {
            Team team = activeTeams.get(i);
            String teamName = ((char) ('A' + i)) + "팀";
            team.setTeamName(teamName);
            
            Player master = Bukkit.getPlayer(team.getMasterId());
            if (master != null) {
                org.bukkit.Color color = NicknameCommand.getColorFromDisplayName(master.getDisplayName());
                team.setTeamColor(color);
                
                String customName = originalDisplayNames.containsKey(master.getUniqueId()) ? originalDisplayNames.get(master.getUniqueId()) : master.getName();
                ChatColor chatColor = NicknameCommand.getChatColorFromDisplayName(master.getDisplayName());
                
                // 탭리스트 및 채팅용 이름 (팀 숨김)
                String pureDisplayName = chatColor + customName;
                // 네임태그용 이름 (팀 표시)
                String nametagName = ChatColor.GRAY + "[" + teamName + "] " + pureDisplayName;
                
                master.setDisplayName(pureDisplayName); // 채팅창에는 팀 이름 제외
                master.setPlayerListName(pureDisplayName); // 탭리스트에는 팀 이름 제외
                NameTagManager.setCustomName(master, nametagName, false); // 네임태그에만 팀 이름 포함, 탭리스트/채팅 미적용
            }
        }

        for (int i = 0; i < activeTeams.size(); i++) {
            Team current = activeTeams.get(i);
            Team next = activeTeams.get((i + 1) % activeTeams.size());
            current.setTargetTeam(next);
        }

        isGameRunning = true;
        
        if (worldBorderSize > 0) {
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                org.bukkit.WorldBorder border = w.getWorldBorder();
                border.setCenter(w.getSpawnLocation());
                border.setSize(worldBorderSize);
            }
        }

        if (testMode) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[테스트 모드] " + ChatColor.GREEN + "꼬리잡기 게임이 시작되었습니다!");
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "꼬리잡기 게임이 시작되었습니다!");
        }
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }

        // 플레이어 랜덤 텔레포트 (월드보더 범위 안, 서로 100블록 이상 이격)
        List<Location> usedLocations = new ArrayList<>();
        java.util.Random random = new java.util.Random();
        
        for (Player p : players) {
            org.bukkit.World w = p.getWorld();
            org.bukkit.WorldBorder border = w.getWorldBorder();
            double halfSize = border.getSize() / 2.0 - 32; // 벽에서 32블록 여유
            
            Location spawnLoc = null;
            int attempts = 0;
            while (attempts < 50) {
                int x = (int) (border.getCenter().getX() + (random.nextDouble() * 2 - 1) * halfSize);
                int z = (int) (border.getCenter().getZ() + (random.nextDouble() * 2 - 1) * halfSize);
                int y = w.getHighestBlockYAt(x, z) + 1;
                Location candidate = new Location(w, x + 0.5, y, z + 0.5);
                
                boolean tooClose = false;
                for (Location used : usedLocations) {
                    if (candidate.distance(used) < 100) {
                        tooClose = true;
                        break;
                    }
                }
                
                if (!tooClose) {
                    spawnLoc = candidate;
                    usedLocations.add(spawnLoc);
                    break;
                }
                attempts++;
            }
            
            // 50번 시도 실패 시 그냥 마지막 위치로 배정
            if (spawnLoc == null) {
                int x = (int) (border.getCenter().getX() + (random.nextDouble() * 2 - 1) * halfSize);
                int z = (int) (border.getCenter().getZ() + (random.nextDouble() * 2 - 1) * halfSize);
                spawnLoc = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
            }
            
            p.teleport(spawnLoc);
        }

        for (Team t : activeTeams) {
            Player master = Bukkit.getPlayer(t.getMasterId());
            Player targetMaster = Bukkit.getPlayer(t.getTargetTeam().getMasterId());
            if (master != null && targetMaster != null) {
                // 팀 이름 안내
                master.sendMessage(ChatColor.AQUA + "══════════════════════");
                master.sendMessage(ChatColor.YELLOW + "당신의 팀: " + ChatColor.GOLD + ChatColor.BOLD + t.getTeamName());
                master.sendMessage(ChatColor.YELLOW + "당신의 타겟: " + ChatColor.RED + ChatColor.BOLD + t.getTargetTeam().getTeamName());
                master.sendMessage(ChatColor.YELLOW + "반드시 타겟을 공격해 쓰러뜨리세요!");
                master.sendMessage(ChatColor.AQUA + "══════════════════════");
                master.sendTitle(
                        ChatColor.GOLD + t.getTeamName(),
                        ChatColor.RED + "타겟: " + t.getTargetTeam().getTeamName(),
                        10, 80, 20
                );
            }
        }
        
        // 팀과 타겟 안내가 끝난 후 마지막에 능력 설명 출력
        for (Player p : players) {
            abilityManager.sendAbilityInfo(p);
        }

        startDistanceTask();
        startFreezeTask();
        startHeartbeatTask();
    }

    public void stopGame() {
        isGameRunning = false;
        isTestMode = false;
        
        // 빙결된 플레이어 복구
        for (UUID uuid : new ArrayList<>(frozenPlayers.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                unfreezePlayer(p);
            }
        }

        playerTeamMap.clear();
        activeTeams.clear();
        frozenPlayers.clear();
        for (ArmorStand stand : freezeHolograms.values()) {
            if (stand != null) stand.remove();
        }
        freezeHolograms.clear();

        if (distanceTask != -1) { Bukkit.getScheduler().cancelTask(distanceTask); distanceTask = -1; }
        if (freezeTask != -1) { Bukkit.getScheduler().cancelTask(freezeTask); freezeTask = -1; }
        if (heartbeatTask != -1) { Bukkit.getScheduler().cancelTask(heartbeatTask); heartbeatTask = -1; }
        
        for (org.bukkit.scheduler.BukkitTask task : disconnectTimers.values()) {
            task.cancel();
        }
        disconnectTimers.clear();
        
        // 능력 제거
        for (Player p : Bukkit.getOnlinePlayers()) {
            abilityManager.onGameEnd(p);
        }
        abilityManager.clear();
        
        Bukkit.broadcastMessage(ChatColor.RED + "꼬리잡기 게임이 종료되었습니다.");
        
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, true);
            w.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, true);
            w.setGameRule(org.bukkit.GameRule.REDUCED_DEBUG_INFO, false); // 좌표 표시 복구
            if (worldBorderSize > 0) {
                w.getWorldBorder().reset();
            }
        }
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, false);
            w.setGameRule(org.bukkit.GameRule.LOG_ADMIN_COMMANDS, false);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule locator_bar true");
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.SEND_COMMAND_FEEDBACK, true);
            w.setGameRule(org.bukkit.GameRule.LOG_ADMIN_COMMANDS, true);
        }
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
            }
            
            // 원래 설정했던 닉네임으로 복구 ([A팀] 등의 접두사 제거)
            String origName = originalDisplayNames.get(p.getUniqueId());
            if (origName != null && !origName.equals(p.getName())) {
                NameTagManager.setCustomName(p, origName);
                p.setDisplayName(origName);
            } else {
                NameTagManager.removeCustomName(p);
                p.setDisplayName(p.getName());
            }

            if (originalListNames.containsKey(p.getUniqueId())) {
                p.setPlayerListName(originalListNames.get(p.getUniqueId()));
            }

            // 노예였던 사람의 가죽 갑옷 제거
            if (playerTeamMap.containsKey(p.getUniqueId())) {
                Team t = playerTeamMap.get(p.getUniqueId());
                if (t.getSlaves().contains(p.getUniqueId())) {
                    p.getInventory().setArmorContents(null);
                }
            }
        }
    }

    public boolean canCatch(Player killer, Player victim) {
        Team killerTeam = getTeamOf(killer);
        Team victimTeam = getTeamOf(victim);
        if (killerTeam == null || victimTeam == null) return false;
        
        return killerTeam.getTargetTeam().equals(victimTeam) && victimTeam.getMasterId().equals(victim.getUniqueId());
    }

    public void handleCatch(Player killer, Player victim) {
        if (!isGameRunning) return;

        Team killerTeam = getTeamOf(killer);
        Team victimTeam = getTeamOf(victim);

        if (killerTeam == null || victimTeam == null) return;
        
        unfreezePlayer(victim);
        
        if (victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(10.0); // 최대 체력 5칸
        }
        victim.setHealth(10.0);
        
        // 닉네임 우선순위 적용
        String victimName = originalDisplayNames.containsKey(victim.getUniqueId()) ? originalDisplayNames.get(victim.getUniqueId()) : victim.getName();
        String killerName = originalDisplayNames.containsKey(killer.getUniqueId()) ? originalDisplayNames.get(killer.getUniqueId()) : killer.getName();

        // 채팅 알림은 전체 공지가 아닌 킬러에게만 (그리고 희생자에게는 아래에서 안내됨)
        killer.sendMessage(ChatColor.GOLD + victimName + ChatColor.GOLD + "님을 잡았습니다! 이제 " + victimName + ChatColor.GOLD + "님은 노예입니다.");
        killer.playSound(killer.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        
        victim.sendMessage(ChatColor.DARK_RED + killerName + ChatColor.DARK_RED + "님에게 잡혀 노예가 되었습니다. (최대 체력이 5칸으로 감소합니다.)");
        victim.sendTitle(ChatColor.DARK_RED + "사망!", ChatColor.GRAY + killerName + ChatColor.GRAY + "의 노예가 되었습니다.", 10, 70, 20);
        
        victim.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        victim.spawnParticle(Particle.TOTEM_OF_UNDYING, victim.getLocation().add(0, 1, 0), 100, 0.5, 0.5, 0.5, 0.5);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));

        // 노예 닉네임 변경 및 갑옷 착용 (NameTagManager로 영어 이름 완전 숨김)
        ChatColor victimChatColor = NicknameCommand.getChatColorFromDisplayName(victim.getDisplayName());
        String slaveName = ChatColor.GRAY + "[노예] " + victimChatColor + victimName;
        victim.setDisplayName(slaveName);
        victim.setPlayerListName(slaveName);
        NameTagManager.setCustomName(victim, slaveName);
        
        equipSlaveArmor(victim, killerTeam.getTeamColor());

        killerTeam.addSlave(victimTeam.getMasterId());
        killerTeam.addSlaves(victimTeam.getSlaves());
        
        // 이전 노예들의 닉네임도 업데이트하고 갑옷 변경
        for (UUID slaveId : victimTeam.getSlaves()) {
            Player s = Bukkit.getPlayer(slaveId);
            if (s != null) {
                String sName = originalDisplayNames.containsKey(s.getUniqueId()) ? originalDisplayNames.get(s.getUniqueId()) : s.getName();
                s.sendMessage(ChatColor.YELLOW + "주인이 " + killerName + ChatColor.YELLOW + "님으로 변경되었습니다!");
                ChatColor sChatColor = NicknameCommand.getChatColorFromDisplayName(s.getDisplayName());
                String sSlaveDisplayName = ChatColor.GRAY + "[노예] " + sChatColor + sName;
                s.setDisplayName(sSlaveDisplayName);
                s.setPlayerListName(sSlaveDisplayName);
                NameTagManager.setCustomName(s, sSlaveDisplayName);
                equipSlaveArmor(s, killerTeam.getTeamColor());
            }
        }

        Team newTarget = victimTeam.getTargetTeam();
        if (newTarget.equals(killerTeam)) {
            if (isTestMode) {
                Player killerMaster = Bukkit.getPlayer(killerTeam.getMasterId());
                if (killerMaster != null) {
                    killerMaster.sendMessage(ChatColor.YELLOW + "[테스트 모드] 모든 타겟을 잡았습니다! (게임이 종료되지 않습니다)");
                }
            } else {
                celebrateVictory(killerTeam);
                stopGame();
                return;
            }
        }

        killerTeam.setTargetTeam(newTarget);
        activeTeams.remove(victimTeam);
        
        playerTeamMap.put(victimTeam.getMasterId(), killerTeam);
        for(UUID slaveId : victimTeam.getSlaves()) {
            playerTeamMap.put(slaveId, killerTeam);
        }

        Player killerMaster = Bukkit.getPlayer(killerTeam.getMasterId());
        Player targetMaster = Bukkit.getPlayer(killerTeam.getTargetTeam().getMasterId());
        if (killerMaster != null && targetMaster != null) {
            killerMaster.sendMessage(ChatColor.AQUA + "══════════════════════");
            killerMaster.sendMessage(ChatColor.YELLOW + "다음 타겟 팀은 " + ChatColor.RED + ChatColor.BOLD + killerTeam.getTargetTeam().getTeamName() + ChatColor.YELLOW + " 입니다!");
            killerMaster.sendMessage(ChatColor.AQUA + "══════════════════════");
            killerMaster.playSound(killerMaster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            killerMaster.sendTitle(ChatColor.GOLD + "타겟 변경!", "새로운 타겟: " + killerTeam.getTargetTeam().getTeamName(), 10, 70, 20);
        }

    }

    private void celebrateVictory(Team winningTeam) {
        Player winner = Bukkit.getPlayer(winningTeam.getMasterId());
        String winnerName = (winner != null) ? (originalDisplayNames.containsKey(winner.getUniqueId()) ? originalDisplayNames.get(winner.getUniqueId()) : winner.getName()) : "알 수 없는 플레이어";

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(winnerName + ChatColor.AQUA + " 우승!", ChatColor.YELLOW + "모든 플레이어를 노예로 만들었습니다!", 20, 100, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.0f);
        }

        Bukkit.broadcastMessage(ChatColor.AQUA + "===============================");
        Bukkit.broadcastMessage(ChatColor.AQUA + "  축하합니다! " + winnerName + ChatColor.AQUA + "팀이 꼬리잡기에서 최종 승리했습니다!");
        Bukkit.broadcastMessage(ChatColor.AQUA + "===============================");

        if (winner != null) {
            spawnFireworks(winner.getLocation());
        }
    }

    private void spawnFireworks(Location loc) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(FireworkEffect.builder().withColor(Color.AQUA).withColor(Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).withFlicker().withTrail().build());
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);
        });
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta fwm = fw.getFireworkMeta();
            fwm.addEffect(FireworkEffect.builder().withColor(Color.RED).withColor(Color.WHITE).with(FireworkEffect.Type.STAR).withFlicker().build());
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);
        }, 20L);
    }

    public Team getTeamOf(Player p) {
        return playerTeamMap.get(p.getUniqueId());
    }

    public void useTracker(Player player) {
        UUID playerId = player.getUniqueId();

        if (player.hasCooldown(org.bukkit.Material.DIAMOND)) {
            return;
        }

        Team team = getTeamOf(player);
        if (team == null || team.getTargetTeam() == null) {
            player.sendMessage(ChatColor.RED + "타겟이 없습니다!");
            return;
        }

        Player target = Bukkit.getPlayer(team.getTargetTeam().getMasterId());
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "타겟이 온라인이 아닙니다!");
            return;
        }

        if (!player.getWorld().equals(target.getWorld())) {
            player.sendMessage(ChatColor.GOLD + "타겟이 다른 월드에 있습니다!");
            return;
        }

        // 다이아몬드 소모
        org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() == org.bukkit.Material.DIAMOND) {
            handItem.setAmount(handItem.getAmount() - 1);
        }

        player.setCooldown(org.bukkit.Material.DIAMOND, 200);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.0f);

        Location playerLoc = player.getLocation().add(0, 1.0, 0);
        
        org.bukkit.util.Vector direction;
        if (player.equals(target)) {
            // 혼자 남았거나(자신이 타겟) 테스트 모드 1인일 때 무조건 바라보는 앞 방향으로 표시
            direction = player.getLocation().getDirection().normalize();
        } else {
            Location targetLoc = target.getLocation().add(0, 1.0, 0);
            org.bukkit.util.Vector diff = targetLoc.toVector().subtract(playerLoc.toVector());
            if (diff.lengthSquared() == 0) return;
            direction = diff.normalize();
        }
        
        // 플레이어 앞 방향으로 입자 생성 (1~4m)
        for (double d = 1.0; d <= 4.0; d += 0.1) {
            Location particleLoc = playerLoc.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 5, 0.05, 0.05, 0.05, 0);
        }
    }

    private void startDistanceTask() {
        distanceTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!isGameRunning) return;

            for (Team team : activeTeams) {
                Player master = Bukkit.getPlayer(team.getMasterId());
                if (master == null || !master.isOnline()) continue;

                for (UUID slaveId : team.getSlaves()) {
                    Player slave = Bukkit.getPlayer(slaveId);
                    if (slave != null && slave.isOnline() && !slave.isDead() && !isFrozen(slave)) {
                        if (!slave.getWorld().equals(master.getWorld()) || slave.getLocation().distance(master.getLocation()) > 32.0) {
                            slave.damage(2.0);
                            slave.sendMessage(ChatColor.RED + "주인님에게서 32블록 이상 멀어져 대미지를 입고 있습니다!");
                            slave.playSound(slave.getLocation(), Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    private void startHeartbeatTask() {
        // 10틱(0.5초)마다 실행: A가 타겟 B에게 가까이 오면 B에게 심장 효과
        heartbeatTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!isGameRunning) return;

            for (Team attackerTeam : activeTeams) {
                Player attacker = Bukkit.getPlayer(attackerTeam.getMasterId());
                if (attacker == null || !attacker.isOnline() || attacker.isDead()) continue;

                Team targetTeam = attackerTeam.getTargetTeam();
                if (targetTeam == null) continue;

                Player targetPlayer = Bukkit.getPlayer(targetTeam.getMasterId());
                if (targetPlayer == null || !targetPlayer.isOnline() || targetPlayer.isDead()) continue;

                if (!attacker.getWorld().equals(targetPlayer.getWorld())) continue;

                double distance = attacker.getLocation().distance(targetPlayer.getLocation());

                // 40블록 이내일 때 심장 효과 (거리에 비례한 강도)
                if (distance <= 40) {
                    // 거리가 가까울수록 강도 증가 (0~1)
                    double intensity = 1.0 - (distance / 40.0);
                    float volume = (float) (0.3 + intensity * 0.7);
                    float pitch = (float) (0.8 + intensity * 0.4);

                    // 액션바에 심장 아이콘 표시 (거리에 따라 다른 색)
                    String hearts;
                    if (distance <= 10) {
                        hearts = ChatColor.RED + "❤ ❤ ❤  누군가 아주 가까이 있습니다!  ❤ ❤ ❤";
                    } else if (distance <= 25) {
                        hearts = ChatColor.GOLD + "❤ ❤  누군가 가까이 있습니다  ❤ ❤";
                    } else {
                        hearts = ChatColor.YELLOW + "❤  누군가 주변에 있습니다  ❤";
                    }

                    targetPlayer.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(hearts)
                    );
                    targetPlayer.playSound(targetPlayer.getLocation(),
                            Sound.ENTITY_WARDEN_HEARTBEAT, volume, pitch);
                }
            }
        }, 10L, 10L);
    }

    private void startFreezeTask() {
        freezeTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!isGameRunning) return;
            long now = System.currentTimeMillis();
            
            Iterator<Map.Entry<UUID, Long>> it = frozenPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                Player p = Bukkit.getPlayer(playerId);
                
                if (p == null || !p.isOnline()) {
                    it.remove();
                    ArmorStand stand = freezeHolograms.remove(playerId);
                    if (stand != null) stand.remove();
                    continue;
                }
                
                long timeLeft = entry.getValue() - now;
                if (timeLeft <= 0) {
                    it.remove();
                    unfreezePlayer(p);
                    p.sendMessage(ChatColor.GREEN + "정지 상태가 해제되었습니다!");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
                } else {
                    int secondsLeft = (int) (timeLeft / 1000) + 1;
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.RED + "정지 상태: " + secondsLeft + "초 남음"));
                    
                    ArmorStand stand = freezeHolograms.get(playerId);
                    if (stand != null && stand.isValid()) {
                        stand.setCustomName(ChatColor.RED + "얼어붙음: " + secondsLeft + "초");
                        stand.teleport(p.getLocation().add(0, 2.2, 0));
                    }
                }
            }
        }, 10L, 10L);
    }

    private void equipSlaveArmor(Player player, org.bukkit.Color color) {
        org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET);
        org.bukkit.inventory.meta.LeatherArmorMeta hMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) helmet.getItemMeta();
        if (hMeta != null) { hMeta.setColor(color); hMeta.setUnbreakable(true); helmet.setItemMeta(hMeta); }

        org.bukkit.inventory.ItemStack chest = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta cMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) chest.getItemMeta();
        if (cMeta != null) { cMeta.setColor(color); cMeta.setUnbreakable(true); chest.setItemMeta(cMeta); }

        org.bukkit.inventory.ItemStack legs = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta lMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) legs.getItemMeta();
        if (lMeta != null) { lMeta.setColor(color); lMeta.setUnbreakable(true); legs.setItemMeta(lMeta); }

        org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_BOOTS);
        org.bukkit.inventory.meta.LeatherArmorMeta bMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
        if (bMeta != null) { bMeta.setColor(color); bMeta.setUnbreakable(true); boots.setItemMeta(bMeta); }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
    }

    public void startDisconnectTimer(Player player) {
        if (!isGameRunning) return;
        Team team = getTeamOf(player);
        // 마스터(주인)이면서 살아있는 팀일 경우에만 타이머 작동
        if (team != null && team.getMasterId().equals(player.getUniqueId()) && activeTeams.contains(team)) {
            Bukkit.broadcastMessage(ChatColor.RED + "[경고] " + ChatColor.YELLOW + player.getName() + "님이 게임에서 나갔습니다! 3분 내로 접속하지 않으면 탈락됩니다!");
            
            org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                disconnectTimers.remove(player.getUniqueId());
                eliminateDisconnectedTeam(team);
            }, 20L * 180); // 3분 (180초)
            
            disconnectTimers.put(player.getUniqueId(), task);
        }
    }

    public void cancelDisconnectTimer(Player player) {
        if (disconnectTimers.containsKey(player.getUniqueId())) {
            disconnectTimers.get(player.getUniqueId()).cancel();
            disconnectTimers.remove(player.getUniqueId());
            Bukkit.broadcastMessage(ChatColor.GREEN + "[알림] " + ChatColor.YELLOW + player.getName() + "님이 재접속하여 탈락 위기를 넘겼습니다!");
        }
    }

    /** 재접속한 플레이어의 탭 리스트, 채팅 이름, 네임태그를 복원 */
    public void restorePlayerNames(Player player) {
        Team team = getTeamOf(player);
        if (team == null) return;
        
        String customName = originalDisplayNames.containsKey(player.getUniqueId()) 
                ? originalDisplayNames.get(player.getUniqueId()) : player.getName();
        ChatColor chatColor = NicknameCommand.getChatColorFromDisplayName(
                customName.length() > 2 ? customName : ChatColor.WHITE + customName);
        // originalDisplayNames에 저장된 이름에서 색상 코드 추출
        if (originalDisplayNames.containsKey(player.getUniqueId())) {
            String orig = originalDisplayNames.get(player.getUniqueId());
            chatColor = NicknameCommand.getChatColorFromDisplayName(chatColor + orig);
        }
        
        boolean isSlave = team.getSlaves().contains(player.getUniqueId());
        
        if (isSlave) {
            String slaveName = ChatColor.GRAY + "[노예] " + chatColor + customName;
            player.setDisplayName(slaveName);
            player.setPlayerListName(slaveName);
            NameTagManager.setCustomName(player, slaveName);
        } else {
            String pureDisplayName = chatColor + customName;
            String nametagName = ChatColor.GRAY + "[" + team.getTeamName() + "] " + pureDisplayName;
            
            player.setDisplayName(pureDisplayName); // 채팅창에는 팀 이름 제외
            player.setPlayerListName(pureDisplayName); // 탭리스트에는 팀 이름 제외
            NameTagManager.setCustomName(player, nametagName, false); // 네임태그에만 팀 이름 포함
        }
    }

    private void eliminateDisconnectedTeam(Team disconnectedTeam) {
        if (!activeTeams.contains(disconnectedTeam)) return;

        Bukkit.broadcastMessage(ChatColor.RED + "[탈락] " + ChatColor.YELLOW + disconnectedTeam.getTeamName() + " (주인 " + disconnectedTeam.getMasterId() + ") 팀이 시간 초과로 탈락했습니다!");
        
        // 이 팀을 쫓던 사냥꾼 찾기
        Team hunterTeam = null;
        for (Team t : activeTeams) {
            if (t.getTargetTeam() != null && t.getTargetTeam().equals(disconnectedTeam)) {
                hunterTeam = t;
                break;
            }
        }
        
        Team newTarget = disconnectedTeam.getTargetTeam();
        
        if (hunterTeam != null) {
            // 사냥꾼에게 노예 흡수 (옵션 A)
            hunterTeam.addSlaves(disconnectedTeam.getSlaves());
            for (UUID slaveId : disconnectedTeam.getSlaves()) {
                playerTeamMap.put(slaveId, hunterTeam);
                Player s = Bukkit.getPlayer(slaveId);
                if (s != null) {
                    s.sendMessage(ChatColor.YELLOW + "원래 주인이 탈락하여, 사냥꾼 팀으로 주인이 변경되었습니다!");
                    equipSlaveArmor(s, hunterTeam.getTeamColor());
                }
            }
            
            hunterTeam.setTargetTeam(newTarget);
            
            Player hunterMaster = Bukkit.getPlayer(hunterTeam.getMasterId());
            if (hunterMaster != null) {
                hunterMaster.sendMessage(ChatColor.AQUA + "══════════════════════");
                hunterMaster.sendMessage(ChatColor.YELLOW + "타겟이 탈락하여 다음 타겟 팀은 " + ChatColor.RED + ChatColor.BOLD + newTarget.getTeamName() + ChatColor.YELLOW + " 입니다!");
                hunterMaster.sendMessage(ChatColor.AQUA + "══════════════════════");
                hunterMaster.playSound(hunterMaster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                hunterMaster.sendTitle(ChatColor.GOLD + "타겟 변경!", "새로운 타겟: " + newTarget.getTeamName(), 10, 70, 20);
            }
            
            if (newTarget.equals(hunterTeam)) {
                if (!isTestMode) {
                    celebrateVictory(hunterTeam);
                    stopGame();
                    return;
                } else {
                    if (hunterMaster != null) hunterMaster.sendMessage(ChatColor.YELLOW + "[테스트 모드] 모든 타겟이 제거되었습니다!");
                }
            }
        }
        
        activeTeams.remove(disconnectedTeam);
    }
}
