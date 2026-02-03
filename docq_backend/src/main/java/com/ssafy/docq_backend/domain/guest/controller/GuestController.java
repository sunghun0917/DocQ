package com.ssafy.docq_backend.domain.guest.controller;

import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.guest.service.GuestService;
// import jakarta.servlet.http.HttpSession; // Removed unused import
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;

@RestController
@RequestMapping("/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    // 게스트 로그인: POST /guests/login
    // Security Filter Chain의 GuestLoginFilter에서 처리됨
    /*
     * @PostMapping("/login")
     * public ResponseEntity<GuestDto.GuestLoginResponse> login(
     * 
     * @Valid @RequestBody GuestDto.GuestLoginRequest request,
     * HttpSession session) {
     * return ResponseEntity.ok(guestService.login(request, session));
     * }
     */

    @PutMapping("/{playerId}/nickname")
    public ResponseEntity<GuestDto.GuestNicknameUpdateResponse> updateNickname(
            @PathVariable String playerId,
            @Valid @RequestBody GuestDto.GuestNicknameUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {

        // 본인 확인
        if (user == null || !user.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(guestService.updateNickname(playerId, request));
    }
}
