package com.ssafy.docq_backend.domain.minigame.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuffType {
    DOUBLE_SCORE("DOUBLE_SCORE", "점수 2배"),
    PLUS_THREE("PLUS_THREE", "+3칸 이동");

    private final String code;
    private final String description;
}
