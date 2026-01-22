package com.ssafy.docq_backend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
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
        private Map<Integer, String> mapEvents;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class AvatarSelectRequest {
        private String roomId;
        private String userId;
        private Integer avatarId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AvatarUpdateResponse {
        private String userId;
        private Integer avatarId;
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
        private Integer avatarId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuizMessage {
        private Long quizId;
        private String question;
        private List<String> options; // 4 options
        private long timeLimit; // ms
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class SubmitAnswer {
        private String roomId;
        private String userId;
        private Long quizId;
        private String answer; // or index

        // 서버에서 기록하는 제출 시간
        private LocalDateTime submitTime;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RoundResult {
        private Long quizId;
        private String correctAnswer;
        private boolean isTurnUserCorrect;
        private String winnerUserId;
        private String rouletteType; // BUFF, MOVE, NONE
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MoveInfo {
        private String userId;
        private int startPosition;
        private int endPosition;
        private List<MoveAction> moveProcess;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MoveAction {
        private String type; // ROULETTE, BUFF, TILE_EFFECT
        private int value; // for ROULETTE
        private String buffType; // for BUFF
        private String effectType; // for TILE_EFFECT
        private Integer before;
        private Integer after;
        private String targetUserId; // For CATCH effect
    }

}
