package com.ssafy.docq_backend.domain.game.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventProcessor {
    private final GameService gameService;
    private final ObjectMapper objectMapper;

    public void handle(GameEvent event) {
        switch (event.getType()) {
            case START_QUIZ -> gameService.startQuiz(event.getRoomId());
            case PROCESS_ROUND_RESULT -> {
                RoundResultPayload payload = objectMapper.convertValue(event.getPayload(), RoundResultPayload.class);
                if (payload.getQuizId() == null) {
                    log.warn("PROCESS_ROUND_RESULT missing quizId for room {}", event.getRoomId());
                    return;
                }
                gameService.processRoundResult(event.getRoomId(), payload.getQuizId());
            }
            case APPLY_MOVE -> {
                ApplyMovePayload payload = objectMapper.convertValue(event.getPayload(), ApplyMovePayload.class);
                gameService.applyMove(event.getRoomId(), payload);
            }
            case NEXT_TURN -> {
                NextTurnPayload payload = objectMapper.convertValue(event.getPayload(), NextTurnPayload.class);
                gameService.prepareNextTurn(event.getRoomId(), payload);
            }
            case RESUME_AFTER_MINIGAME -> {
                ResumeAfterMiniGamePayload payload = objectMapper.convertValue(event.getPayload(), ResumeAfterMiniGamePayload.class);
                gameService.resumeAfterMiniGame(event.getRoomId(), payload);
            }
            default -> log.warn("Unknown event type {}", event.getType());
        }
    }
}
