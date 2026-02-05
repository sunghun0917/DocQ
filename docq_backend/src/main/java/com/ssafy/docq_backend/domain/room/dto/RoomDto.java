package com.ssafy.docq_backend.domain.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class RoomDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomCreateRequest {
        @Min(value = 2, message = "최소 인원은 2명입니다.")
        private int capacity;

        @NotBlank(message = "방 제목은 필수입니다.")
        private String title;

        private Boolean isPrivate;
        private String password;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RoomCreateResponse {
        private String roomId;
        private String inviteCode;
        private String hostPlayerId;
        private Boolean isPrivate;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RoomResponse {
        private String roomId;
        private String title;
        private Boolean isPrivate;
        private String status;
        private int currentCount;
        private int capacity;
        private int quizCount; // 퀴즈 개수 확인용
        @JsonProperty("isGenerating")
        private boolean isGenerating;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomJoinRequest {
        @NotBlank(message = "roomId는 필수입니다.")
        private String roomId;
        private String password;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RoomJoinResponse {
        private String roomId;
        private String roomCode;
        private String status;
    }
}
