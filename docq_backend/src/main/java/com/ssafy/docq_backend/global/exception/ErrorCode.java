package com.ssafy.docq_backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    /* C001 ~ C002 : 공통 (Common) */
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "서버 내부 에러"),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C002", "잘못된 입력값"),

    /* R001 ~ R006 : 방 (Room) */
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "존재하지 않는 방"),
    ROOM_FULL(HttpStatus.BAD_REQUEST, "R002", "방 인원 초과"),
    GAME_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "R003", "이미 시작된 게임"),
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "R004", "게임 정보 없음"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "R005", "비밀번호가 일치하지 않음"),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "R006", "비밀번호를 입력해주세요."),
    NOT_ENOUGH_PLAYERS(HttpStatus.BAD_REQUEST, "R007", "인원이 부족합니다. (최소 2명)"),
    NOT_ALL_READY(HttpStatus.BAD_REQUEST, "R008", "모든 플레이어가 준비되지 않았습니다."),

    /* U001 : 사용자 (User) */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 사용자"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U002", "닉네임 중복"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "U003", "권한이 없습니다."),

    /* G001 : 게임 로직 (Game) */
    NOT_YOUR_TURN(HttpStatus.BAD_REQUEST, "G001", "턴 오류"),
    ALREADY_SUBMITTED(HttpStatus.BAD_REQUEST, "G002", "이미 제출했습니다"),
    GHOST_OPTIONS_HIDDEN(HttpStatus.BAD_REQUEST, "G003", "고스트 보기 공개 전입니다."),
    TURN_SUBMISSION_CLOSED(HttpStatus.BAD_REQUEST, "G004", "턴 응답 시간이 종료되었습니다."),

    /* D001 ~ D003 : 문서/AI (Document) */
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "D001", "파일 업로드 실패"),
    AI_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "D002", "AI 처리 실패"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "D003", "파일 형식 오류"),

    /* A001 ~ A003 : 인증 (Auth) */
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "A001", "이미 사용중인 로그인 ID입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A002", "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A003", "로그인이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}