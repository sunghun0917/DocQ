package com.ssafy.docq_backend.domain.quiz.controller;

import com.ssafy.docq_backend.domain.quiz.dto.QuizApiDto;
import com.ssafy.docq_backend.domain.quiz.service.QuizService;
import com.ssafy.docq_backend.domain.storedquiz.service.StoredQuizService;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 퀴즈 생성 및 저장 API.
 * SecurityConfig: /quizzes/** hasRole("USER") — 회원만 접근.
 * 사용자 식별: @AuthenticationPrincipal CustomUserDetails.
 */
@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final StoredQuizService storedQuizService;

    @PostMapping("/create")
    public ResponseEntity<QuizApiDto.CreateQuizResponse> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody QuizApiDto.CreateQuizRequest request) {
        Long userId = user != null ? user.getUserId() : null;
        QuizApiDto.CreateQuizResponse response = quizService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{quizId}/store")
    public ResponseEntity<QuizApiDto.StoreQuizResponse> store(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long quizId) {
        Long userId = user != null ? user.getUserId() : null;
        QuizApiDto.StoreQuizResponse response = storedQuizService.store(userId, quizId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 저장 취소 (언저장). 회원 전용.
     */
    @DeleteMapping("/{quizId}/store")
    public ResponseEntity<QuizApiDto.StoreQuizResponse> unstore(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long quizId) {
        Long userId = user != null ? user.getUserId() : null;
        QuizApiDto.StoreQuizResponse response = storedQuizService.unstore(userId, quizId);
        return ResponseEntity.ok(response);
    }

    /**
     * 내가 만든 퀴즈 목록 조회. 회원 전용.
     */
    @GetMapping("/my")
    public ResponseEntity<QuizApiDto.MyQuizListResponse> getMyQuizzes(
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = user != null ? user.getUserId() : null;
        QuizApiDto.MyQuizListResponse response = quizService.getMyQuizzes(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 내가 저장한 퀴즈 목록 조회. 회원 전용.
     */
    @GetMapping("/stored-quizz")
    public ResponseEntity<QuizApiDto.MyQuizListResponse> getStoredQuizzes(
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = user != null ? user.getUserId() : null;
        QuizApiDto.MyQuizListResponse response = storedQuizService.getStoredQuizzes(userId);
        return ResponseEntity.ok(response);
    }
}
