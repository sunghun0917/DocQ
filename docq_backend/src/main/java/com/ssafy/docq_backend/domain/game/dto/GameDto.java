package com.ssafy.docq_backend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
        private String playerId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveRequest {
        private String roomId;
        private String playerId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndGameRequest {
        private String roomId;
        private String playerId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestartRequest {
        private String roomId;
        private String playerId;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadyRequest {
        private String roomId;
        private String playerId;
        private boolean isReady;
    }

    public enum AckType {
        RESULT_DISPLAYED,
        ROULETTE_COMPLETE,
        MOVE_COMPLETE,
        MINIGAME_COMPLETE,
        BUFF_ROULETTE_COMPLETE,
        CAMERA_END // 추가
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AckRequest {
        private String roomId;
        private String playerId;
        private AckType ackType;
        private Long quizId;
        private Integer round;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GameStateResponse {
        private RoomStatus state;
        private Integer round;
        private String currentTurnPlayerId;
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
        private String playerId;
        private Integer avatarId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AvatarUpdateResponse {
        private String playerId;
        private Integer avatarId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlayerInfo {
        private String playerId;
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
    public static class RoomClosedResponse {
        private String roomId;
        private String reason; // HOST_END, HOST_LEFT, EMPTY
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
        private OffsetDateTime quizStartTime;
        private Long quizStartTimeMs;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class QuizOptionsMessage {
        private Long quizId;
        private List<String> options;
        private Long extraTimeMs;
    }

    /** 턴 플레이어에게만 전송하는 즉시 정답/오답 피드백 */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class AnswerFeedbackMessage {
        private boolean correct; // true: 맞췄습니다, false: 틀렸습니다
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class SubmitAnswer {
        private String roomId;
        private String playerId;
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
        private String winnerPlayerId;
        private String rouletteType; // BUFF, MOVE, NONE
        private Integer rouletteValue;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MoveInfo {
        private String playerId;
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
        private String targetPlayerId; // For CATCH effect
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FinishResponse {
        private String type; // "FINISH"
        private String winnerPlayerId;
        private List<FinishRank> rankings;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FinishRank {
        private String playerId;
        private String nickname;
        private int position;
        private int remaining;
        private int score;
        private int rank;
    }

}
