package com.ssafy.docq_backend.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class QuizApiDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizItemRequest {
        @NotNull(message = "문제 내용은 필수입니다.")
        private String question;

        @NotEmpty(message = "보기는 1개 이상이어야 합니다.")
        private List<String> options;

        @NotNull(message = "정답 인덱스는 필수입니다.")
        private Integer answerIndex;

        @NotNull(message = "정답 텍스트는 필수입니다.")
        private String answerText;

        
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateQuizRequest {
        @NotEmpty(message = "퀴즈 목록은 1개 이상이어야 합니다.")
        @Valid
        private List<QuizItemRequest> quizzes;
        
        private String title; // 퀴즈 제목 (선택적)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateQuizResponse {
        private Long quizId;
        private int quizCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreQuizResponse {
        private Long quizId;
        private boolean stored;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyQuizItem {
        private Long quizId;
        private String title; // 퀴즈 제목
        private int questionCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyQuizListResponse {
        private List<MyQuizItem> quizzes;
    }
}

