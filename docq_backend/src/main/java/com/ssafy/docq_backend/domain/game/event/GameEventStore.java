package com.ssafy.docq_backend.domain.game.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventStore {
    private static final String ZSET_KEY = "game:events";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration EVENT_TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GameEvent schedule(String roomId, GameEventType type, Object payload, long delayMs) {
        Map<String, Object> payloadMap = payload == null
                ? Collections.emptyMap()
                : objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});

        GameEvent event = GameEvent.builder()
                .id(UUID.randomUUID().toString())
                .roomId(roomId)
                .type(type)
                .payload(payloadMap)
                .attempt(0)
                .build();

        long scheduledAt = System.currentTimeMillis() + Math.max(0, delayMs);
        event.setScheduledAt(scheduledAt);
        storeEvent(event);
        return event;
    }

    public List<GameEvent> popDueEvents(int limit) {
        long now = System.currentTimeMillis();
        Set<String> ids = redisTemplate.opsForZSet().rangeByScore(ZSET_KEY, 0, now, 0, limit);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<GameEvent> events = new ArrayList<>();
        for (String id : ids) {
            if (!tryLock(id)) {
                continue;
            }
            redisTemplate.opsForZSet().remove(ZSET_KEY, id);
            GameEvent event = loadEvent(id);
            if (event == null || event.isProcessed()) {
                releaseLock(id);
                continue;
            }
            events.add(event);
        }
        return events;
    }

    public void markProcessed(String eventId) {
        redisTemplate.opsForHash().put(eventKey(eventId), "processed", "1");
        redisTemplate.expire(eventKey(eventId), EVENT_TTL);
        releaseLock(eventId);
    }

    public void reschedule(GameEvent event, long delayMs) {
        event.setAttempt(event.getAttempt() + 1);
        event.setScheduledAt(System.currentTimeMillis() + Math.max(0, delayMs));
        storeEvent(event);
    }

    private void storeEvent(GameEvent event) {
        String key = eventKey(event.getId());
        try {
            String payloadJson = objectMapper.writeValueAsString(event.getPayload());
            redisTemplate.opsForHash().put(key, "id", event.getId());
            redisTemplate.opsForHash().put(key, "roomId", event.getRoomId());
            redisTemplate.opsForHash().put(key, "type", event.getType().name());
            redisTemplate.opsForHash().put(key, "payload", payloadJson);
            redisTemplate.opsForHash().put(key, "scheduledAt", String.valueOf(event.getScheduledAt()));
            redisTemplate.opsForHash().put(key, "attempt", String.valueOf(event.getAttempt()));
            redisTemplate.opsForHash().put(key, "processed", "0");
            redisTemplate.opsForZSet().add(ZSET_KEY, event.getId(), event.getScheduledAt());
            redisTemplate.expire(key, EVENT_TTL);
        } catch (Exception e) {
            log.error("Failed to store event {}", event.getId(), e);
        }
    }

    private GameEvent loadEvent(String eventId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(eventKey(eventId));
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        try {
            String payloadJson = String.valueOf(fields.getOrDefault("payload", "{}"));
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
            return GameEvent.builder()
                    .id(String.valueOf(fields.get("id")))
                    .roomId(String.valueOf(fields.get("roomId")))
                    .type(GameEventType.valueOf(String.valueOf(fields.get("type"))))
                    .payload(payload)
                    .scheduledAt(Long.parseLong(String.valueOf(fields.get("scheduledAt"))))
                    .attempt(Integer.parseInt(String.valueOf(fields.getOrDefault("attempt", "0"))))
                    .processed("1".equals(String.valueOf(fields.getOrDefault("processed", "0"))))
                    .build();
        } catch (Exception e) {
            log.error("Failed to load event {}", eventId, e);
            return null;
        }
    }

    private boolean tryLock(String eventId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey(eventId), "1", LOCK_TTL));
    }

    private void releaseLock(String eventId) {
        redisTemplate.delete(lockKey(eventId));
    }

    private String eventKey(String eventId) {
        return "game:event:" + eventId;
    }

    private String lockKey(String eventId) {
        return "game:event:" + eventId + ":lock";
    }
}
