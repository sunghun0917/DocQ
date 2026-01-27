package com.ssafy.docq_backend.domain.game.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.game.dto.GameDto;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AckStore {
    private static final Duration DEFAULT_ACK_TTL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_PAYLOAD_TTL = Duration.ofSeconds(120);
    private static final Duration DEFAULT_EXEC_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_ACK_COMPLETE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public long addAckAndCount(String roomId, GameDto.AckType ackType, String keyId, String userId) {
        String key = ackKey(roomId, ackType, keyId);
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, DEFAULT_ACK_TTL);
        Long count = redisTemplate.opsForSet().size(key);
        return count == null ? 0L : count;
    }

    public void clearAck(String roomId, GameDto.AckType ackType, String keyId) {
        redisTemplate.delete(ackKey(roomId, ackType, keyId));
        redisTemplate.delete(ackPayloadKey(roomId, ackType, keyId));
    }

    public boolean tryMarkAckComplete(String roomId, GameDto.AckType ackType, String keyId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(ackCompleteKey(roomId, ackType, keyId), "1", DEFAULT_ACK_COMPLETE_TTL));
    }

    public boolean tryMarkExecution(String roomId, String phase, String keyId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(execKey(roomId, phase, keyId), "1", DEFAULT_EXEC_TTL));
    }

    public void savePayload(String roomId, GameDto.AckType ackType, String keyId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(ackPayloadKey(roomId, ackType, keyId), json, DEFAULT_PAYLOAD_TTL);
        } catch (Exception e) {
            log.error("Failed to save ack payload for {} {}", roomId, ackType, e);
        }
    }

    public <T> T loadPayload(String roomId, GameDto.AckType ackType, String keyId, Class<T> type) {
        String json = redisTemplate.opsForValue().get(ackPayloadKey(roomId, ackType, keyId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to load ack payload for {} {}", roomId, ackType, e);
            return null;
        }
    }

    public Map<String, Object> loadPayloadMap(String roomId, GameDto.AckType ackType, String keyId) {
        String json = redisTemplate.opsForValue().get(ackPayloadKey(roomId, ackType, keyId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to load ack payload map for {} {}", roomId, ackType, e);
            return null;
        }
    }

    public void clearRoomData(String roomId) {
        try {
            Set<String> keys = new HashSet<>();
            keys.addAll(scanKeys("ack:" + roomId + ":*"));
            keys.addAll(scanKeys("ack:payload:" + roomId + ":*"));
            keys.addAll(scanKeys("ack:complete:" + roomId + ":*"));
            keys.addAll(scanKeys("exec:" + roomId + ":*"));
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Failed to clear ack data for room {}", roomId, e);
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                connection -> connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build()))) {
            if (cursor == null) {
                return keys;
            }
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            log.error("Failed to scan keys with pattern {}", pattern, e);
        }
        return keys;
    }

    private String ackKey(String roomId, GameDto.AckType ackType, String keyId) {
        return "ack:" + roomId + ":" + ackType.name() + ":" + keyId;
    }

    private String ackPayloadKey(String roomId, GameDto.AckType ackType, String keyId) {
        return "ack:payload:" + roomId + ":" + ackType.name() + ":" + keyId;
    }

    private String ackCompleteKey(String roomId, GameDto.AckType ackType, String keyId) {
        return "ack:complete:" + roomId + ":" + ackType.name() + ":" + keyId;
    }

    private String execKey(String roomId, String phase, String keyId) {
        return "exec:" + roomId + ":" + phase + ":" + keyId;
    }
}
