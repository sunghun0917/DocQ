package com.ssafy.docq_backend.global.client.controller;

import com.ssafy.docq_backend.domain.game.service.GameService;
import com.ssafy.docq_backend.global.client.dto.AiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiCallbackController {

    private final GameService gameService;

    @PostMapping("/callback")
    public ResponseEntity<Void> handleAiCallback(@RequestBody AiDto.CallbackRequest request) {
        log.info("AI callback received: jobId={}, status={}", request.getJobId(), request.getStatus());

        try {
            if ("completed".equals(request.getStatus())) {
                gameService.handleAiQuizResult(request.getJobId(), request.getQuizzes());
            } else {
                gameService.handleAiJobFailed(request.getJobId(), request.getError());
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error processing AI callback", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
