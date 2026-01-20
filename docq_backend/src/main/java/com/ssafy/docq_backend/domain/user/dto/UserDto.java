package com.ssafy.docq_backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLoginRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class UserLoginResponse {
        private String userId;
        private String nickname;
    }
}
