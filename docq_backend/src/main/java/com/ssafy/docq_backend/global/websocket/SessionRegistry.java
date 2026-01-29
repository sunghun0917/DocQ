package com.ssafy.docq_backend.global.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userToSession = new ConcurrentHashMap<>();

    public void register(String sessionId, String roomId, String userId) {
        if (sessionId == null || roomId == null || userId == null) {
            return;
        }
        sessions.put(sessionId, new SessionInfo(roomId, userId));
        userToSession.put(userId, sessionId);
    }

    public SessionInfo removeBySessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            userToSession.remove(info.userId);
        }
        return info;
    }

    public void removeByUserId(String userId) {
        if (userId == null) {
            return;
        }
        String sessionId = userToSession.remove(userId);
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    @Getter
    public static class SessionInfo {
        private final String roomId;
        private final String userId;

        public SessionInfo(String roomId, String userId) {
            this.roomId = roomId;
            this.userId = userId;
        }
    }
}
