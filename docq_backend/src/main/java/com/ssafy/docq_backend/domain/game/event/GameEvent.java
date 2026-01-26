package com.ssafy.docq_backend.domain.game.event;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEvent {
    private String id;
    private String roomId;
    private GameEventType type;
    private Map<String, Object> payload;
    private long scheduledAt;
    private int attempt;
    private boolean processed;
}
