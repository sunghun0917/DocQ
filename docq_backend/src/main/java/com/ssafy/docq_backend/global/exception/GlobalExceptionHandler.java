package com.ssafy.docq_backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 우리가 직접 정의한 비즈니스 예외 처리 (가장 많이 사용됨)
    @ExceptionHandler(DocqException.class)
    public ResponseEntity<ErrorResponse> handleDocqException(DocqException e) {
        log.error("DocqException: Code: {}, Message: {}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // 2. @Valid 어노테이션 유효성 검사 실패 시 (C002)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation Error: {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 3. 예측하지 못한 모든 에러 (C001 - 최후의 방어선)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Unhandled Exception: ", e); // 스택 트레이스 출력
        return ErrorResponse.toResponseEntity(ErrorCode.SERVER_ERROR);
    }
}