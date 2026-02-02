package com.ssafy.docq_backend.global.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> playerToSession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomPlayers = new ConcurrentHashMap<>();

    public void register(String sessionId, String roomId, String playerId) {
        if (sessionId == null || roomId == null || playerId == null) {
            return;
        }
        sessions.put(sessionId, new SessionInfo(roomId, playerId));
        playerToSession.put(playerId, sessionId);
        roomPlayers.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public SessionInfo removeBySessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            String currentSession = playerToSession.get(info.playerId);
            if (sessionId.equals(currentSession)) {
                playerToSession.remove(info.playerId);
                removeFromRoom(info.roomId, info.playerId);
                return info;
            }
            return null;
        }
        return info;
    }

    public void removeByPlayerId(String playerId) {
        if (playerId == null) {
            return;
        }
        String sessionId = playerToSession.remove(playerId);
        if (sessionId != null) {
            SessionInfo info = sessions.remove(sessionId);
            if (info != null) {
                removeFromRoom(info.roomId, info.playerId);
            }
        }
    }

    public int getConnectedCount(String roomId) {
        if (roomId == null) {
            return 0;
        }
        Set<String> players = roomPlayers.get(roomId);
        return players == null ? 0 : players.size();
    }

    private void removeFromRoom(String roomId, String playerId) {
        if (roomId == null || playerId == null) {
            return;
        }
        Set<String> players = roomPlayers.get(roomId);
        if (players == null) {
            return;
        }
        players.remove(playerId);
        if (players.isEmpty()) {
            roomPlayers.remove(roomId);
        }
    }

    @Getter
    public static class SessionInfo {
        private final String roomId;
        private final String playerId;

        public SessionInfo(String roomId, String playerId) {
            this.roomId = roomId;
            this.playerId = playerId;
        }
    }
}
