package com.koma.tailcatch;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TextDisplay를 승객(Passenger)으로 태워 머리 위 네임태그 구현
 *  - 틱 단위 텔레포트가 아닌 승객 처리이므로 끊김(Stuttering)이 전혀 없음
 *  - 본인에게는 보이지 않도록 숨김 처리
 */
public class NameTagManager implements Listener {

    private static final Map<UUID, String> customNames = new HashMap<>();
    private static final Map<UUID, TextDisplay> nameTagDisplays = new HashMap<>();
    private static final String HIDE_TEAM = "tc_hide_nametag";
    private static JavaPlugin pluginInstance;

    public static void init(JavaPlugin plugin) {
        pluginInstance = plugin;
        ensureHideTeam();
        Bukkit.getPluginManager().registerEvents(new NameTagManager(), plugin);
    }

    @SuppressWarnings("deprecation")
    private static Team ensureHideTeam() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(HIDE_TEAM);
        if (team == null) {
            team = board.registerNewTeam(HIDE_TEAM);
        }
        team.setNameTagVisibility(NameTagVisibility.NEVER); // 원래 영어 이름 숨김
        team.setPrefix("");
        team.setSuffix("");
        team.setCanSeeFriendlyInvisibles(true); // 투명화된 플레이어 서로 반투명하게 보이도록 설정
        return team;
    }

    @SuppressWarnings("deprecation")
    public static void setCustomName(Player player, String displayName, boolean updateTabAndChat) {
        customNames.put(player.getUniqueId(), displayName);

        // 1. 바닐라 네임태그 숨김
        Team hideTeam = ensureHideTeam();
        hideTeam.addEntry(player.getName());

        // 2. 탭 리스트 및 채팅 이름 설정 (선택적)
        if (updateTabAndChat) {
            player.setPlayerListName(displayName);
            player.setDisplayName(displayName);
        }

        // 3. TextDisplay 승객 생성
        removeTextDisplay(player.getUniqueId());
        spawnTextDisplay(player, displayName);
    }

    public static void setCustomName(Player player, String displayName) {
        setCustomName(player, displayName, true);
    }

    @SuppressWarnings("deprecation")
    public static void removeCustomName(Player player) {
        customNames.remove(player.getUniqueId());
        removeTextDisplay(player.getUniqueId());

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hideTeam = board.getTeam(HIDE_TEAM);
        if (hideTeam != null) {
            hideTeam.removeEntry(player.getName());
        }

        player.setPlayerListName(player.getName());
        player.setDisplayName(player.getName());
    }

    private static void spawnTextDisplay(Player player, String name) {
        Location loc = player.getLocation();
        TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setText(name);
            td.setBillboard(Display.Billboard.CENTER); // 항상 플레이어를 향함
            td.setSeeThrough(false);
            td.setShadowed(true);
            td.setDefaultBackground(true); // 마인크래프트 기본 반투명 검은색 배경 사용
            
            // 승객으로 탔을 때 Y축 위치 조정 (기존 0.45에서 0.3으로 낮춤)
            org.bukkit.util.Transformation transform = td.getTransformation();
            transform.getTranslation().set(0f, 0.3f, 0f); 
            td.setTransformation(transform);
        });
        
        // 승객으로 태우면 틱 지연 없이 완벽하게 따라다님
        player.addPassenger(display);
        
        // 자신에게는 이 디스플레이를 숨겨서 시야를 가리지 않게 함
        player.hideEntity(pluginInstance, display);
        
        nameTagDisplays.put(player.getUniqueId(), display);
    }

    private static void removeTextDisplay(UUID uuid) {
        TextDisplay display = nameTagDisplays.remove(uuid);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    public static String getCustomName(UUID uuid) {
        return customNames.get(uuid);
    }

    public static TextDisplay getTextDisplay(UUID uuid) {
        return nameTagDisplays.get(uuid);
    }

    public static String getDisplayName(Player player) {
        String custom = customNames.get(player.getUniqueId());
        return (custom != null) ? custom : player.getName();
    }

    // 수영, 침대, 포탈 등으로 인해 강제로 내려질 경우 다시 태움
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof TextDisplay && event.getDismounted() instanceof Player player) {
            TextDisplay display = nameTagDisplays.get(player.getUniqueId());
            if (display != null && display.equals(event.getEntity())) {
                Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                    if (player.isOnline() && !display.isDead()) {
                        player.addPassenger(display);
                    }
                }, 1L);
            }
        }
    }

    // 텔레포트시 디스플레이도 같이 텔레포트 및 재탑승
    @EventHandler
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = nameTagDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            display.teleport(event.getTo());
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                if (player.isOnline() && !display.isDead()) {
                    player.addPassenger(display);
                }
            }, 1L);
        }
    }

    // 리스폰시
    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = nameTagDisplays.get(player.getUniqueId());
        if (display != null && !display.isDead()) {
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                display.teleport(player.getLocation());
                player.addPassenger(display);
            }, 2L);
        }
    }

    public static void shutdown() {
        for (TextDisplay display : nameTagDisplays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        nameTagDisplays.clear();
        customNames.clear();
        
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hideTeam = board.getTeam(HIDE_TEAM);
        if (hideTeam != null) {
            hideTeam.unregister();
        }
    }

    // 플레이어 퇴장 시 TextDisplay 제거 (공중에 남는 것 방지)
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeTextDisplay(player.getUniqueId());
    }

    // 플레이어 재접속 시 커스텀 이름이 저장되어 있으면 TextDisplay 재생성
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = customNames.get(player.getUniqueId());
        if (name != null) {
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                if (player.isOnline()) {
                    // 바닐라 네임태그 다시 숨김
                    Team hideTeam = ensureHideTeam();
                    hideTeam.addEntry(player.getName());
                    
                    // TextDisplay 재생성
                    removeTextDisplay(player.getUniqueId());
                    spawnTextDisplay(player, name);
                }
            }, 5L); // 5틱 후 (플레이어 로딩 완료 대기)
        }
    }
}
