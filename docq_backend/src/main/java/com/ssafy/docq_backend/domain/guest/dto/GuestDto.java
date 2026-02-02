package com.ssafy.docq_backend.domain.guest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class GuestDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestLoginRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class GuestLoginResponse {
        private String playerId;
        private String nickname;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestNicknameUpdateRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class GuestNicknameUpdateResponse {
        private String playerId;
        private String nickname;
    }
}
