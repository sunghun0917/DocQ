package com.ssafy.docq_backend.domain.game.controller;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.dto.QuizDto;
import com.ssafy.docq_backend.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

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

    @ResponseBody
    @PostMapping("/api/game/{roomId}/quiz/generate")
    public String generateQuiz(@PathVariable String roomId, @RequestParam("file") MultipartFile file) {
        // TODO: userId 검증 (방장인지 등)
        gameService.generateQuizFromPdf(roomId, file);
        return "Quiz generation started";
    }

    @ResponseBody
    @GetMapping("/api/game/{roomId}/quiz")
    public java.util.List<QuizDto.Response> getQuizzes(@PathVariable String roomId) {
        return gameService.getQuizzes(roomId);
    }
}
