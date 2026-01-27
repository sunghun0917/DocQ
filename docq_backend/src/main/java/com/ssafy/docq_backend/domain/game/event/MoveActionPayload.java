package com.ssafy.docq_backend.domain.game.event;

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
public class MoveActionPayload {
    private String type;
    private Integer value;
    private String buffType;
    private String effectType;
    private Integer before;
    private Integer after;
    private String targetUserId;
}
