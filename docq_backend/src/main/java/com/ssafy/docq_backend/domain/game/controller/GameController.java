package com.ssafy.docq_backend.domain.game.controller;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

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
}
