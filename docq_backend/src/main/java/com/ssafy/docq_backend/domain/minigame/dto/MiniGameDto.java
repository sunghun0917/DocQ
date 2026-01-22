package com.ssafy.docq_backend.domain.minigame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class MiniGameDto {

    @Getter
    @Builder
    public static class StartResponse {
        private String type; // "START"
        private String gameType; // "SPACE_MASH"
        private int timeLimit; // 5000
        private String description;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubmissionRequest {
        private String roomId;
        private String userId;
        private String gameType; // "SPACE_MASH"
        private int resultValue;
    }

    @Getter
    @Builder
    public static class ResultResponse {
        private String type; // "RESULT" 또는 "MINIGAME_END"
        private String gameType; // "SPACE_MASH"
        private String winnerUserId;
        private String rewardBuff; // "DOUBLE_SCORE"
        private List<RankInfo> rankings;
    }

    @Getter
    @Builder
    public static class RankInfo {
        private String userId;
        private String nickname;
        private int value;
        private int rank;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouletteRequest {
        private String roomId;
        private String userId;
    }
}
