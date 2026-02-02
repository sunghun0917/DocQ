package com.ssafy.docq_backend.domain.game.event;

import java.util.List;

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
public class MovePlanPayload {
    private String winnerUserId;
    private int startPosition;
    private int endPosition;
    private boolean consumeDoubleMove;
    private List<MoveActionPayload> actions;
}
