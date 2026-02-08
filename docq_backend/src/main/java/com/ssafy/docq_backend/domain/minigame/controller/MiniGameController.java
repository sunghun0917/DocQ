package com.ssafy.docq_backend.domain.minigame.controller;

import com.ssafy.docq_backend.domain.minigame.dto.MiniGameDto;
import com.ssafy.docq_backend.domain.minigame.service.MiniGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MiniGameController {

    private final MiniGameService miniGameService;

    // 클라이언트 전송 경로: /app/game/minigame/submit
    @MessageMapping("/game/minigame/submit")
    public void submitMiniGameResult(@Payload MiniGameDto.SubmissionRequest request) {
        log.info("MiniGame Result Submitted: Room={}, Player={}, Value={}", request.getRoomId(), request.getPlayerId(),
                request.getResultValue());
        miniGameService.submitResult(request);
    }

    // 클라이언트 전송 경로: /app/game/minigame/item
    @MessageMapping("/game/minigame/item")
    public void useItem(@Payload MiniGameDto.ItemUsageRequest request) {
        log.info("Item Usage Request: Room={}, Player={}, Item={}, TargetP={}, TargetPos={}",
                request.getRoomId(), request.getPlayerId(), request.getItemType(), request.getTargetPlayerId(),
                request.getTargetPosition());
        miniGameService.useItem(request);
    }
}
