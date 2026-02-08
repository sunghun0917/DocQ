package com.ssafy.docq_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AuthDto {

    // ========== 회원가입 ==========
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        @NotBlank(message = "로그인 ID는 필수입니다.")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SignupResponse {
        private Long id;
        private String loginId;
        private String nickname;
        private String playerId;  // 게임용 playerId (UUID)
        private LocalDateTime createdAt;
    }

    // ========== 로그인 ==========
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "로그인 ID는 필수입니다.")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LoginResponse {
        private Long id;
        private String loginId;
        private String nickname;
        private String playerId;  // 게임용 playerId (UUID)
    }
}
