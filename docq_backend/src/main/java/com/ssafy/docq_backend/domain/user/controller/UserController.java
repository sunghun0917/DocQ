package com.ssafy.docq_backend.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.ssafy.docq_backend.domain.user.dto.UserDto;
import com.ssafy.docq_backend.domain.user.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<UserDto.UserLoginResponse> login(@Valid @RequestBody UserDto.UserLoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PutMapping("/{userId}/nickname")
    public ResponseEntity<UserDto.UserNicknameUpdateResponse> updateNickname(
            @PathVariable String userId,
            @Valid @RequestBody UserDto.UserNicknameUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateNickname(userId, request));
    }
}
