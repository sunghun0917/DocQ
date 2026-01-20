package com.ssafy.docq_backend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import com.ssafy.docq_backend.domain.room.entity.RoomStatus;

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
