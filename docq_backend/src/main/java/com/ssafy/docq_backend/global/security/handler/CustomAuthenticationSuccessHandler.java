package com.ssafy.docq_backend.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.auth.dto.AuthDto;
import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final PlayerRepository playerRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 1. PlayerId 생성 (매 로그인/인증 시 새로 생성 - 요구사항에 따름)
        // 기존 코드에서는 매 로그인마다 새로운 playerId를 발급했음
        String playerId = UUID.randomUUID().toString();

        // 2. Player 엔티티 저장
        Player player;
        if (userDetails.isGuest()) {
            player = Player.guest(playerId, userDetails.getNickname());
            // 게스트 세션 타임아웃 설정 (1시간) - HttpSession에 설정
            request.getSession().setMaxInactiveInterval(3600);
        } else {
            player = Player.member(playerId, userDetails.getNickname(), userDetails.getUserId());
        }
        playerRepository.save(player);

        // 3. CustomUserDetails에 playerId 설정 (SecurityContext에 반영됨)
        userDetails.setPlayerId(playerId);

        // * SecurityContext는 이미 요청 처리가 끝나면 HttpSession에 자동 저장되므로
        // 여기서 Principal(userDetails)의 상태를 변경하면 세션에도 반영됨.

        // 응답 생성
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        if (userDetails.isGuest()) {
            GuestDto.GuestLoginResponse guestResponse = GuestDto.GuestLoginResponse.builder()
                    .playerId(playerId)
                    .nickname(userDetails.getNickname())
                    .build();
            objectMapper.writeValue(response.getWriter(), guestResponse);
        } else {
            AuthDto.LoginResponse loginResponse = AuthDto.LoginResponse.builder()
                    .id(userDetails.getUserId())
                    .loginId(userDetails.getUsername())
                    .nickname(userDetails.getNickname())
                    .playerId(playerId)
                    .build();
            objectMapper.writeValue(response.getWriter(), loginResponse);
        }
    }
}
