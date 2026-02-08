package com.ssafy.docq_backend.domain.game.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private String playerId;

    @Builder.Default
    private int score = 0;

    @Builder.Default
    private int position = 0;

    @Builder.Default
    private String hasBuff = null; // null means no buff
}
