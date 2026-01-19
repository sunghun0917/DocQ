package com.ssafy.docq_backend.controller;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class TestGameController {

    // 1. 클라이언트가 "/app/test"로 메시지를 보내면 이 메서드가 실행됩니다.
    // (Prefix '/app'은 config에서 설정했으므로 생략하고 뒤만 적습니다)
    @MessageMapping("/test")

    // 2. 이 메서드의 리턴값은 "/topic/test"를 구독하고 있는 모든 사람에게 뿌려집니다.
    @SendTo("/topic/test")
    public Map<String, String> echoMessage(Map<String, String> message) {
        // 클라이언트가 보낸 메시지를 로그로 확인
        System.out.println("받은 메시지: " + message.get("content"));

        // 다시 클라이언트에게 응답 (Echo)
        return Map.of("content", "서버 응답: " + message.get("content"));
    }
}
