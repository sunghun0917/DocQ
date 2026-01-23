package com.ssafy.docq_backend.domain.game.controller;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.dto.QuizDto;
import com.ssafy.docq_backend.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
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

    @MessageMapping("/game/join")
    public void joinGame(GameDto.JoinRequest request) {
        gameService.joinGame(request);
    }

    @MessageMapping("/game/leave")
    public void leaveGame(GameDto.LeaveRequest request) {
        gameService.leaveGame(request);
    }

    @MessageMapping("/game/ready")
    public void readyGame(GameDto.ReadyRequest request) {
        gameService.readyGame(request);
    }

    @MessageMapping("/game/start")
    public void startGame(Map<String, String> payload) {
        // Payload: { "roomId": "...", "userId": "..." }
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        gameService.startGame(roomId, userId);
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
    public ResponseEntity<?> generateQuiz(@PathVariable String roomId, @RequestParam("file") MultipartFile file) {
        try {
            // TODO: userId 검증 (방장인지 등)
            gameService.generateQuizFromPdf(roomId, file);
            return ResponseEntity.ok("Quiz generation started");
        } catch (Exception e) {
            log.error("Quiz generation failed", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message", e.getMessage()));
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
}
