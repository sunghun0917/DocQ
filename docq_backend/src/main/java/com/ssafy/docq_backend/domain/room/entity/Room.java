package com.ssafy.docq_backend.domain.room.entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Room {
    private String id;
    private String hostId;
    private String title;
    private int capacity;
    private boolean isPrivate;
    private String password;
    private String inviteCode;

    @Builder.Default
    private RoomStatus status = RoomStatus.WAITING;

    @Builder.Default
    private int currentCount = 0;

    @Builder.Default
    private Set<String> players = ConcurrentHashMap.newKeySet();

    @Builder.Default
    private Set<String> readyPlayers = ConcurrentHashMap.newKeySet();

    public void addPlayer(String userId) {
        players.add(userId);
        this.currentCount = players.size();
    }

    public void removePlayer(String userId) {
        players.remove(userId);
        readyPlayers.remove(userId); // 나가면 준비 상태도 해제
        this.currentCount = players.size();
    }

    public boolean isReady(String userId) {
        return readyPlayers.contains(userId);
    }

    public void setReady(String userId, boolean ready) {
        if (ready) {
            readyPlayers.add(userId);
        } else {
            readyPlayers.remove(userId);
        }
    }
}
