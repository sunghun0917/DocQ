package com.ssafy.docq_backend.domain.player.repository;

import com.ssafy.docq_backend.domain.player.entity.Player;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player In-Memory Repository
 * 게임 세션 동안 플레이어 정보 저장
 */
@Repository
public class PlayerRepository {
    private final Map<String, Player> store = new ConcurrentHashMap<>();

    public void save(Player player) {
        store.put(player.getPlayerId(), player);
    }

    public Optional<Player> findById(String playerId) {
        return Optional.ofNullable(store.get(playerId));
    }

    public void deleteById(String playerId) {
        store.remove(playerId);
    }

    public boolean existsByNickname(String nickname) {
        return store.values().stream()
                .anyMatch(player -> player.getNickname().equals(nickname));
    }
}
