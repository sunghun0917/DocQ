package com.ssafy.docq_backend.global.client.dto;

import lombok.*;
import java.util.List;

public class AiDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class JobResponse {
        private String jobId;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class CallbackRequest {
        private String jobId;
        private String status;  // "completed" or "failed"
        private Integer total;
        private List<QuizItem> quizzes;
        private String error;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class QuizItem {
        private String question;
        private List<String> options;
        private int answerIndex;
        private String answerText;
        private String explanation;
    }
}
