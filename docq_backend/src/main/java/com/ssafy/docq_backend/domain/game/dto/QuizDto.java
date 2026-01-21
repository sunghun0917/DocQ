package com.ssafy.docq_backend.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

public class QuizDto {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String question;
        private List<String> options;
        private int answerIndex;
        private String answerText;
        private String explanation;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String quizId;
        private String question;
        private List<String> options;
        // 답안은 클라이언트로 보낼 때 숨겨야 할 수도 있지만, 일단 포함
        private String answerText;
        private int answerIndex;
    }
}
