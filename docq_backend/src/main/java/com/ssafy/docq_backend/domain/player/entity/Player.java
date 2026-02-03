package com.ssafy.docq_backend.domain.player.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 게임 참여자 엔티티 (In-Memory)
 * 게스트/회원 모두 이 엔티티로 게임에 참여
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Player {
    /** 세션 UUID (게임/방/웹소켓 API에서 사용) */
    private String playerId;

    /** 닉네임 */
    private String nickname;

    /** 플레이어 유형 (GUEST/MEMBER) */
    private PlayerType type;

    /** 회원인 경우 User.id 참조, 게스트는 null */
    private Long userId;

    /**
     * 게스트 플레이어 생성
     */
    public static Player guest(String playerId, String nickname) {
        return Player.builder()
                .playerId(playerId)
                .nickname(nickname)
                .type(PlayerType.GUEST)
                .userId(null)
                .build();
    }

    /**
     * 회원 플레이어 생성
     */
    public static Player member(String playerId, String nickname, Long userId) {
        return Player.builder()
                .playerId(playerId)
                .nickname(nickname)
                .type(PlayerType.MEMBER)
                .userId(userId)
                .build();
    }

    public boolean isGuest() {
        return type == PlayerType.GUEST;
    }

    public boolean isMember() {
        return type == PlayerType.MEMBER;
    }
}
