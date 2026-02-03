package com.ssafy.docq_backend.domain.minigame.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuffType {
    BANANA("BANANA", "바나나 (함정)"),
    SWAP_POSITION("SWAP_POSITION", "자리 바꾸기"),
    QUIZ_INTERFERENCE("QUIZ_INTERFERENCE", "문제 방해"),
    PLUS_THREE("PLUS_THREE", "+3칸 이동"),
    BOOM("BOOM", "꽝");

    private final String code;
    private final String description;
}
