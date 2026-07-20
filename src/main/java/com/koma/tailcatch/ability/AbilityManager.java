package com.koma.tailcatch.ability;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 능력 시스템 핵심 관리자.
 * 게임 시작 시 닉네임 기반으로 능력 부여, 게임 종료 시 정리.
 */
public class AbilityManager {

    public static final int INITIAL_EMERALDS = 3;   // 게임 시작 시 지급 에메랄드 수
    public static final int MAX_USES = 6;           // 게임 당 최대 사용 횟수
    public static final int COOLDOWN_TICKS = 300;    // 쿨타임 300틱 = 15초

    private final JavaPlugin plugin;

    // 순수 닉네임(소문자) → 능력
    private final Map<String, Ability> abilityRegistry = new HashMap<>();

    // 플레이어별 능력 상태 (쿨타임, 남은 횟수)
    private final Map<UUID, PlayerAbilityState> playerStates = new HashMap<>();

    // 플레이어별 현재 능력
    private final Map<UUID, Ability> playerAbilities = new HashMap<>();

    public AbilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 능력 등록 */
    public void register(Ability ability) {
        abilityRegistry.put(ability.getNickname().toLowerCase(), ability);
    }
    
    public Ability getPlayerAbility(UUID uuid) {
        return playerAbilities.get(uuid);
    }

    /**
     * 게임 시작 시 호출: 닉네임을 확인하고 능력 부여.
     * @param player 게임에 참가한 플레이어
     * @param pureName 색상 코드 제거된 닉네임 (예: "시락")
     */
    public void onGameStart(Player player, String pureName) {
        Ability ability = abilityRegistry.get(pureName.toLowerCase());
        if (ability == null) return;

        playerAbilities.put(player.getUniqueId(), ability);
        playerStates.put(player.getUniqueId(), new PlayerAbilityState(MAX_USES));

        // 패시브 적용
        ability.applyPassive(player);

        // 에메랄드 지급
        player.getInventory().addItem(new ItemStack(Material.EMERALD, INITIAL_EMERALDS));
        
    }

    /** 능력 안내 메시지 출력 (게임 시작 완료 후 호출됨) */
    public void sendAbilityInfo(Player player) {
        Ability ability = playerAbilities.get(player.getUniqueId());
        if (ability == null) return;

        player.sendMessage(ChatColor.GOLD + "══════════════════════");
        player.sendMessage(ChatColor.AQUA + "⚡ 고유 능력: " + ChatColor.YELLOW + ability.getAbilityName());
        player.sendMessage(ChatColor.GREEN + "  패시브: " + ChatColor.WHITE + ability.getPassiveDescription());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  액티브: " + ChatColor.WHITE + ability.getActiveDescription());
        player.sendMessage(ChatColor.GRAY + "  에메랄드 우클릭으로 액티브 사용 (쿨타임 15초, 최대 " + MAX_USES + "회)");
        player.sendMessage(ChatColor.GOLD + "══════════════════════");
    }

    /**
     * 게임 종료 시 호출: 모든 플레이어 정리.
     */
    public void onGameEnd(Player player) {
        Ability ability = playerAbilities.remove(player.getUniqueId());
        playerStates.remove(player.getUniqueId());

        if (ability != null) {
            ability.removePassive(player);
        }

        // 에메랄드 회수
        player.getInventory().remove(Material.EMERALD);
        player.setCooldown(Material.EMERALD, 0);
    }

    /**
     * 에메랄드 우클릭 시 호출.
     * 쿨타임/횟수 체크 후 액티브 발동.
     */
    public void onEmeraldUse(Player player) {
        Ability ability = playerAbilities.get(player.getUniqueId());
        if (ability == null) {
            player.sendMessage(ChatColor.RED + "능력이 없습니다!");
            return;
        }

        PlayerAbilityState state = playerStates.get(player.getUniqueId());
        if (state == null) return;

        // 횟수 체크
        if (state.getUsesLeft() <= 0) {
            player.sendMessage(ChatColor.RED + "이미 최대 횟수를 모두 사용했습니다! (" + MAX_USES + "/" + MAX_USES + ")");
            return;
        }

        // 쿨타임 체크 (getCooldown > 0이면 아직 쿨타임 중)
        int cooldown = player.getCooldown(Material.EMERALD);
        if (cooldown > 0) {
            // 쿨타임 텍스트 생략 (마인크래프트 기본 쿨타임 오버레이만 표시됨)
            return;
        }

        // 에메랄드 1개 소모
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.EMERALD && hand.getAmount() > 0) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            // 손에 없으면 인벤토리에서 소모
            player.getInventory().removeItem(new ItemStack(Material.EMERALD, 1));
        }

        // 사용 횟수 차감 및 쿨타임 적용
        state.use();
        player.setCooldown(Material.EMERALD, COOLDOWN_TICKS);

        // 액티브 발동
        ability.useActive(player);

        // 액션바 알림 대신 채팅으로 표시
        int usesLeft = state.getUsesLeft();
        int usedCount = MAX_USES - usesLeft;
        player.sendMessage(
            ChatColor.AQUA + "⚡ " + ability.getAbilityName() + " 발동! " +
            ChatColor.YELLOW + "(" + usedCount + "/" + MAX_USES + ")"
        );
    }

    /** 특정 플레이어가 능력을 가지고 있는지 확인 */
    public boolean hasAbility(Player player) {
        return playerAbilities.containsKey(player.getUniqueId());
    }

    /** 플레이어의 현재 능력 반환 */
    public Ability getAbility(Player player) {
        return playerAbilities.get(player.getUniqueId());
    }

    /** 전체 초기화 */
    public void clear() {
        playerAbilities.clear();
        playerStates.clear();
    }

    // ─────────────────────────────────────────
    /** 플레이어 능력 상태 (사용 횟수 추적) */
    public static class PlayerAbilityState {
        private int usesLeft;

        public PlayerAbilityState(int maxUses) {
            this.usesLeft = maxUses;
        }

        public int getUsesLeft() {
            return usesLeft;
        }

        public void use() {
            if (usesLeft > 0) usesLeft--;
        }
    }
}
