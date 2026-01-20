package com.ssafy.docq_backend.user.repository;

import com.ssafy.docq_backend.user.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {
    private final Map<String, User> store = new ConcurrentHashMap<>();

    public void save(User user) {
        store.put(user.getId(), user);
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public boolean existsByNickname(String nickname) {
        return store.values().stream()
                .anyMatch(user -> user.getNickname().equals(nickname));
    }
}
