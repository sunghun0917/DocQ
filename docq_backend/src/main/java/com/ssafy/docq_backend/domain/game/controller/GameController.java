package com.ssafy.docq_backend.domain.game.controller;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.dto.QuizDto;
import com.ssafy.docq_backend.domain.game.service.GameService;
import com.ssafy.docq_backend.global.websocket.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final SessionRegistry sessionRegistry;

    @MessageMapping("/game/join")
    public void joinGame(GameDto.JoinRequest request, SimpMessageHeaderAccessor headerAccessor) {
        gameService.joinGame(request);
        if (headerAccessor != null) {
            sessionRegistry.register(headerAccessor.getSessionId(), request.getRoomId(), request.getPlayerId());
        }
    }

    @MessageMapping("/game/leave")
    public void leaveGame(GameDto.LeaveRequest request) {
        gameService.leaveGame(request);
        sessionRegistry.removeByPlayerId(request.getPlayerId());
    }

    @MessageMapping("/game/ready")
    public void readyGame(GameDto.ReadyRequest request) {
        gameService.readyGame(request);
    }

    @MessageMapping("/game/start")
    public void startGame(Map<String, String> payload) {
        // Payload: { "roomId": "...", "playerId": "..." }
        String roomId = payload.get("roomId");
        String playerId = payload.get("playerId");
        gameService.startGame(roomId, playerId);
    }

    @MessageMapping("/game/end")
    public void endGame(GameDto.EndGameRequest request) {
        gameService.endGame(request);
    }

    @MessageMapping("/game/restart")
    public void restartGame(GameDto.RestartRequest request) {
        gameService.restartGame(request);
    }

    @ResponseBody
    @PostMapping("/game/{roomId}/quiz/generate")
    public ResponseEntity<?> generateQuiz(
            @PathVariable String roomId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        try {
            // TODO: playerId 검증 (방장인지 등)
            gameService.generateQuizFromPdf(roomId, file, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Quiz generation started",
                    "roomId", roomId));
        } catch (com.ssafy.docq_backend.global.exception.DocqException e) {
            log.error("Quiz generation failed (Managed): {}", e.getMessage());
            return ResponseEntity
                    .status(e.getErrorCode().getStatus())
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Quiz generation failed", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", e.getMessage()));
        }

    }

    @ResponseBody
    @GetMapping("/game/{roomId}/quiz")
    public java.util.List<QuizDto.Response> getQuizzes(@PathVariable String roomId) {
        return gameService.getQuizzes(roomId);
    }

    @ResponseBody
    @PostMapping("/game/{roomId}/quiz/default")
    public Map<String, Object> setDefaultQuizzes(@PathVariable String roomId,
            @RequestParam("type") String type) {
        int count = gameService.setDefaultQuizzes(roomId, type);
        return Map.of("count", count);
    }

    @MessageMapping("/game/submit")
    public void submitAnswer(GameDto.SubmitAnswer request) {
        gameService.submitAnswer(request);
    }

    @MessageMapping("/game/avatar")
    public void selectAvatar(GameDto.AvatarSelectRequest request) {
        gameService.selectAvatar(request);
    }

    @MessageMapping("/game/ack")
    public void handleAck(GameDto.AckRequest request) {
        gameService.handleAck(request);
    }

    @MessageMapping("/game/quiz/reset")
    public void resetQuiz(Map<String, String> payload) {
        String roomId = payload.get("roomId");
        if (roomId != null) {
            gameService.resetQuiz(roomId);
        }
    }
}
