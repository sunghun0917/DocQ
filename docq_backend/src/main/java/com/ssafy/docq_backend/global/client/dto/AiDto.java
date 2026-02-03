package com.ssafy.docq_backend.global.client.dto;

import lombok.*;

public class AiDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class JobResponse {
        private String jobId;
        private String status; // 외부 응답용 (String 유지)
    }
}
