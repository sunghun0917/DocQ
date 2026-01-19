package com.ssafy.docq_backend.user.controller;

import com.ssafy.docq_backend.user.dto.UserDto;
import com.ssafy.docq_backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<UserDto.UserLoginResponse> login(@Valid @RequestBody UserDto.UserLoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
