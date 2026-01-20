package com.ssafy.docq_backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 소켓 연결 엔드포인트 설정 (API 명세서: ws://{ip}:8080/ws-docq)
        registry.addEndpoint("/ws-docq")
                .setAllowedOrigins("*")
                .withSockJS();
        // SockJS 없이 순수 STOMP만 쓸 경우 아래 줄도 추가 (Postman 테스트용)
        registry.addEndpoint("/ws-docq")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 2. 메시지 구독 요청 url (Subscribe) -> /topic/game/{roomId}/state 등
        registry.enableSimpleBroker("/topic", "/queue");

        // 3. 메시지 발행 요청 url (Publish) -> /app/game/join 등
        registry.setApplicationDestinationPrefixes("/app");
    }
}
