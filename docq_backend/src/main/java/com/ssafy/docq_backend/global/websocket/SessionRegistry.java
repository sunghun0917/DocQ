package com.ssafy.docq_backend.global.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userToSession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    public void register(String sessionId, String roomId, String userId) {
        if (sessionId == null || roomId == null || userId == null) {
            return;
        }
        sessions.put(sessionId, new SessionInfo(roomId, userId));
        userToSession.put(userId, sessionId);
        roomUsers.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public SessionInfo removeBySessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            String currentSession = userToSession.get(info.userId);
            if (sessionId.equals(currentSession)) {
                userToSession.remove(info.userId);
                removeFromRoom(info.roomId, info.userId);
                return info;
            }
            return null;
        }
        return info;
    }

    public void removeByUserId(String userId) {
        if (userId == null) {
            return;
        }
        String sessionId = userToSession.remove(userId);
        if (sessionId != null) {
            SessionInfo info = sessions.remove(sessionId);
            if (info != null) {
                removeFromRoom(info.roomId, info.userId);
            }
        }
    }

    public int getConnectedCount(String roomId) {
        if (roomId == null) {
            return 0;
        }
        Set<String> users = roomUsers.get(roomId);
        return users == null ? 0 : users.size();
    }

    private void removeFromRoom(String roomId, String userId) {
        if (roomId == null || userId == null) {
            return;
        }
        Set<String> users = roomUsers.get(roomId);
        if (users == null) {
            return;
        }
        users.remove(userId);
        if (users.isEmpty()) {
            roomUsers.remove(roomId);
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
