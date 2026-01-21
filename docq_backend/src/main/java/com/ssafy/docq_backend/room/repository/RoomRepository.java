package com.ssafy.docq_backend.room.repository;

import com.ssafy.docq_backend.room.domain.Room;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RoomRepository {
    private final Map<String, Room> store = new ConcurrentHashMap<>();

    public void save(Room room) {
        store.put(room.getId(), room);
    }

    public Optional<Room> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public boolean existsByInviteCode(String inviteCode) {
        return store.values().stream()
                .anyMatch(room -> room.getInviteCode().equals(inviteCode));
    }

    public Optional<Room> findByInviteCode(String inviteCode) {
        return store.values().stream()
                .filter(room -> room.getInviteCode().equals(inviteCode))
                .findFirst();
    }
}
