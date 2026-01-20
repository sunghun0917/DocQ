package com.ssafy.docq_backend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

import com.ssafy.docq_backend.domain.room.entity.RoomStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

public class GameDto {

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRequest {
        private String roomId;
        private String nickname;
        private String userId;
    }

    @Getter
    @Setter
    @ToString
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GameStateResponse {
        private RoomStatus state;
        private Integer round;
        private String currentTurnUserId;
        private List<PlayerInfo> players;
        // turnOrder/cardDrawResult can be removed or kept depending on UI needs.
        // Keeping them for "StarGame" transition context.
        private List<String> turnOrder;
        private Map<String, Integer> cardDrawResult;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlayerInfo {
        private String userId;
        private String nickname;
        private boolean isHost;
        private boolean connected;
        private boolean isReady;

        // New Game Fields
        private Integer score;
        private Integer position;
        private String hasBuff; // null or Enum string
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
    }
}
