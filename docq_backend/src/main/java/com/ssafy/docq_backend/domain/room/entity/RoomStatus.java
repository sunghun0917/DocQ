package com.ssafy.docq_backend.domain.room.entity;

public enum RoomStatus {
    WAITING,
    STARTING,
    QUIZ_TIME,
    JUDGMENT,
    ROULETTE,
    MOVE,
    FINISHED,
    PLAYING, // deprecated but kept for compatibility if needed, or remove if fully replaced.
    MINIGAME
}
