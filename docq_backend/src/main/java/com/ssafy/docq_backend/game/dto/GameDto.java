package com.ssafy.docq_backend.game.dto;

import com.ssafy.docq_backend.room.domain.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class GameDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRequest {
        private String roomId;
        private String nickname;
        private String userId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadyRequest {
        private String roomId;
        private String userId;
        private boolean isReady;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class GameStateResponse {
        private RoomStatus state;
        private List<PlayerInfo> players;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PlayerInfo {
        private String userId;
        private String nickname;
        private boolean isHost;
        private boolean connected;
        private boolean isReady;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
    }
}
