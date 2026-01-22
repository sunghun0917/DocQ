package com.ssafy.docq_backend.domain.room.entity;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.game.entity.Quiz;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
    private int currentCount = 1;

    @Builder.Default
    private Set<String> players = ConcurrentHashMap.newKeySet();

    @Builder.Default
    private Set<String> readyPlayers = ConcurrentHashMap.newKeySet();

    // UserId -> AvatarId
    @Builder.Default
    private Map<String, Integer> playerAvatars = new ConcurrentHashMap<>();

    // Map Events: Index(0~29) -> EventType (e.g. "TILE_X2")
    @Builder.Default
    private Map<Integer, String> mapEvents = new HashMap<>();

    @Builder.Default
    private List<String> turnOrder = new ArrayList<>();

    // userId -> cardValue (1~100)
    @Builder.Default
    private Map<String, Integer> cardDrawResult = new HashMap<>();

    @Builder.Default
    private int round = 1;

    private String currentTurnUserId;

    // --- Quiz State ---
    private Long currentQuizId;
    @Builder.Default
    private Map<String, GameDto.SubmitAnswer> quizAnswers = new ConcurrentHashMap<>();
    private LocalDateTime quizStartTime;

    // userId -> PlayerState
    @Builder.Default
    private Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();

    // 퀴즈 목록
    @Builder.Default
    private List<Quiz> quizList = new ArrayList<>();

    // MiniGame State: UserId -> Score
    @Builder.Default
    private Map<String, Integer> miniGameScores = new ConcurrentHashMap<>();

    // Played MiniGame Types (to avoid repetition until all played)
    @Builder.Default
    private Set<String> playedMiniGames = ConcurrentHashMap.newKeySet();

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
        return userId.equals(hostId) || readyPlayers.contains(userId);
    }

    public void setReady(String userId, boolean ready) {
        if (ready) {
            readyPlayers.add(userId);
        } else {
            readyPlayers.remove(userId);
        }
    }
}
