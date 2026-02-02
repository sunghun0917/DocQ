package com.ssafy.docq_backend.domain.minigame.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MiniGameType {
    LEFT_RIGHT_MASH("LEFT_RIGHT_MASH", "제한시간 동안 좌우 방향키를 빠르게 연타하세요!", 5000),
    DIRECTION_MATCH("DIRECTION_MATCH", "화면에 나타나는 방향키를 순서대로 입력하세요!", 10000),
    EXACT_TIME("EXACT_TIME", "정확히 5.00초에 맞춰 스톱워치를 멈추세요!", 5000);

    private final String code;
    private final String description;
    private final int timeLimit;
}
