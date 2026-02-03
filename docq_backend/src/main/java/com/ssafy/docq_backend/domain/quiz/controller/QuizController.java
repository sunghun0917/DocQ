package com.ssafy.docq_backend.domain.quiz.controller;

import com.ssafy.docq_backend.domain.quiz.dto.QuizApiDto;
import com.ssafy.docq_backend.domain.quiz.service.QuizService;
import com.ssafy.docq_backend.domain.storedquiz.service.StoredQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 퀴즈 생성 및 저장 API.
 * PDF 파싱 등으로 생성된 퀴즈들을 quizzes 테이블에 저장한다.
 * 현재는 Security 없이 X-User-Id 헤더로 사용자 식별(목업).
 */
@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final StoredQuizService storedQuizService;

    @PostMapping("/create")
    public ResponseEntity<QuizApiDto.CreateQuizResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody QuizApiDto.CreateQuizRequest request
    ) {
        QuizApiDto.CreateQuizResponse response = quizService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{quizId}/store")
    public ResponseEntity<QuizApiDto.StoreQuizResponse> store(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long quizId
    ) {
        QuizApiDto.StoreQuizResponse response = storedQuizService.store(userId, quizId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 저장 취소 (언저장).
     * 회원 전용 (X-User-Id 헤더).
     */
    @DeleteMapping("/{quizId}/store")
    public ResponseEntity<QuizApiDto.StoreQuizResponse> unstore(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long quizId
    ) {
        QuizApiDto.StoreQuizResponse response = storedQuizService.unstore(userId, quizId);
        return ResponseEntity.ok(response);
    }

    /**
     * 내가 만든 퀴즈 목록 조회.
     * 회원 전용 (X-User-Id 헤더).
     */
    @GetMapping("/my")
    public ResponseEntity<QuizApiDto.MyQuizListResponse> getMyQuizzes(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        QuizApiDto.MyQuizListResponse response = quizService.getMyQuizzes(userId);
        return ResponseEntity.ok(response);
    }
}

