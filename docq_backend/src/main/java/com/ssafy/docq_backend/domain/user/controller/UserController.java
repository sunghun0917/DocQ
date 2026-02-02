package com.ssafy.docq_backend.domain.user.controller;

import com.ssafy.docq_backend.domain.user.dto.UserDto;
import com.ssafy.docq_backend.domain.user.service.UserService;
import com.ssafy.docq_backend.global.session.SessionDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 아이디 중복 확인: GET /users/userId/exists?userId=xxx
    @GetMapping("/userId/exists")
    public ResponseEntity<UserDto.LoginIdExistsResponse> checkLoginIdExists(
            @RequestParam String userId) {
        return ResponseEntity.ok(userService.checkLoginIdExists(userId));
    }

    // 닉네임 중복 확인: GET /users/nickname/exists?nickname=xxx
    @GetMapping("/nickname/exists")
    public ResponseEntity<UserDto.NicknameExistsResponse> checkNicknameExists(
            @RequestParam String nickname) {
        return ResponseEntity.ok(userService.checkNicknameExists(nickname));
    }

    // 내 정보 조회: GET /users/me
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(HttpSession session) {
        SessionDto sessionDto = (SessionDto) session.getAttribute("user");
        if (sessionDto == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getMyInfo(sessionDto));
    }

    // 회원 닉네임 변경: PATCH /users/me/nickname
    @PatchMapping("/me/nickname")
    public ResponseEntity<UserDto.NicknameUpdateResponse> updateNickname(
            @Valid @RequestBody UserDto.NicknameUpdateRequest request,
            HttpSession session) {
        SessionDto sessionDto = (SessionDto) session.getAttribute("user");
        if (sessionDto == null || sessionDto.isGuest()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.updateNickname(sessionDto.getId(), request, session));
    }
}
