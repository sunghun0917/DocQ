package com.ssafy.docq_backend.domain.guest.repository;

import com.ssafy.docq_backend.domain.guest.entity.Guest;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class GuestRepository {
    private final Map<String, Guest> store = new ConcurrentHashMap<>();

    public void save(Guest guest) {
        store.put(guest.getId(), guest);
    }

    public Optional<Guest> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public boolean existsByNickname(String nickname) {
        return store.values().stream()
                .anyMatch(guest -> guest.getNickname().equals(nickname));
    }
}
