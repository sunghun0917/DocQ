package com.ssafy.docq_backend.domain.auth.controller;

import com.ssafy.docq_backend.domain.auth.dto.AuthDto;
import com.ssafy.docq_backend.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입: POST /auth/signup
    @PostMapping("/signup")
    public ResponseEntity<AuthDto.SignupResponse> signup(
            @Valid @RequestBody AuthDto.SignupRequest request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(request, session));
    }

    // 회원 로그인: POST /auth/login
    // Security Filter Chain의 JsonUsernamePasswordAuthenticationFilter에서 처리됨
    /*
     * @PostMapping("/login")
     * public ResponseEntity<AuthDto.LoginResponse> login(
     * 
     * @Valid @RequestBody AuthDto.LoginRequest request,
     * HttpSession session) {
     * return ResponseEntity.ok(authService.login(request, session));
     * }
     */

    // 로그아웃: POST /auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }
}
