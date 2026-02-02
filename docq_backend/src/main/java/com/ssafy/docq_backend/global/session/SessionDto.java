package com.ssafy.docq_backend.global.session;

import lombok.Builder;
import lombok.Getter;
import java.io.Serializable;

@Getter
@Builder
public class SessionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId; // UUID - 게임/웹소켓용 (게스트/회원 공통)
    private Long id; // DB PK - 회원만, 게스트는 null
    private String loginId; // 로그인 ID - 회원만
    private String nickname;
    private boolean isGuest;

    public static SessionDto forGuest(String playerId, String nickname) {
        return SessionDto.builder()
                .playerId(playerId)
                .id(null)
                .loginId(null)
                .nickname(nickname)
                .isGuest(true)
                .build();
    }

    public static SessionDto forMember(String playerId, Long id, String loginId, String nickname) {
        return SessionDto.builder()
                .playerId(playerId)
                .id(id)
                .loginId(loginId)
                .nickname(nickname)
                .isGuest(false)
                .build();
    }
}
