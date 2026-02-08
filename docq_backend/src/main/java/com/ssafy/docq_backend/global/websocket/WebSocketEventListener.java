package com.ssafy.docq_backend.global.websocket;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener implements ApplicationListener<SessionDisconnectEvent> {
    private final SessionRegistry sessionRegistry;
    private final GameService gameService;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        SessionRegistry.SessionInfo info = sessionRegistry.removeBySessionId(sessionId);
        if (info == null) {
            return;
        }
        log.info("Disconnect detected. sessionId={}, roomId={}, playerId={}", sessionId, info.getRoomId(), info.getPlayerId());
        gameService.leaveGame(new GameDto.LeaveRequest(info.getRoomId(), info.getPlayerId()));
    }
}
