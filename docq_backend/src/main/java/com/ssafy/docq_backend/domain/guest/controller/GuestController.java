package com.ssafy.docq_backend.domain.guest.controller;

import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.guest.service.GuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    @PostMapping("/login")
    public ResponseEntity<GuestDto.GuestLoginResponse> login(@Valid @RequestBody GuestDto.GuestLoginRequest request) {
        return ResponseEntity.ok(guestService.login(request));
    }

    @PutMapping("/{playerId}/nickname")
    public ResponseEntity<GuestDto.GuestNicknameUpdateResponse> updateNickname(
            @PathVariable String playerId,
            @Valid @RequestBody GuestDto.GuestNicknameUpdateRequest request
    ) {
        return ResponseEntity.ok(guestService.updateNickname(playerId, request));
    }
}
