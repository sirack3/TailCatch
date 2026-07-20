package com.koma.tailcatch.ability;

import org.bukkit.entity.Player;

/**
 * 닉네임 연동 능력 인터페이스.
 * 새 능력을 추가하려면 이 인터페이스를 구현하고 AbilityManager에 등록.
 */
public interface Ability {

    /** 이 능력이 발동될 닉네임 (색상 코드 제거한 순수 이름, 예: "시락") */
    String getNickname();

    /** 능력 이름 (UI 표시용) */
    String getAbilityName();

    /** 패시브 설명 */
    String getPassiveDescription();

    /** 액티브 설명 */
    String getActiveDescription();

    /** 게임 시작 시 패시브 적용 */
    void applyPassive(Player player);

    /** 게임 종료 시 패시브 제거 */
    void removePassive(Player player);

    /** 액티브 사용 (에메랄드 우클릭 시 호출) */
    void useActive(Player player);
}
