package com.ssafy.docq_backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserDto {

    // ========== 아이디 중복 확인 ==========
    @Getter
    @Builder
    @AllArgsConstructor
    public static class LoginIdExistsResponse {
        private boolean available;
        private String message;
    }

    // ========== 닉네임 중복 확인 ==========
    @Getter
    @Builder
    @AllArgsConstructor
    public static class NicknameExistsResponse {
        private boolean exists;
        private String message;
    }

    // ========== 내 정보 조회 (Guest) ==========
    @Getter
    @Builder
    @AllArgsConstructor
    public static class GuestMeResponse {
        private String playerId;
        private String nickname;
        private boolean isGuest;
    }

    // ========== 내 정보 조회 (Member) ==========
    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberMeResponse {
        private Long id;
        private String playerId;
        private String loginId;
        private String nickname;
        private boolean isGuest;
    }

    // ========== 닉네임 변경 ==========
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NicknameUpdateRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NicknameUpdateResponse {
        private String nickname;
    }
}
