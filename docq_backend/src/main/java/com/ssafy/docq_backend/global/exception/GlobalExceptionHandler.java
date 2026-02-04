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

    // 3. 인증 관련 에러 (로그인 실패 등)
    @ExceptionHandler({ org.springframework.security.authentication.BadCredentialsException.class,
            org.springframework.security.authentication.InternalAuthenticationServiceException.class })
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception e) {
        log.error("Authentication Error: {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_CREDENTIALS);
    }

    // 4. 권한 관련 에러 (접근 거부)
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(Exception e) {
        log.error("Access Denied Error: {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.ACCESS_DENIED);
    }

    // 5. DB 무결성 위반 (중복 데이터 등)
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityException(
            org.springframework.dao.DataIntegrityViolationException e) {
        log.error("Database Error: {}", e.getMessage());
        // 구체적인 원인 분석이 어렵다면 일반적인 중복 에러로 처리하거나 SERVER_ERROR
        // 여기서는 예시로 "중복된 값이 존재합니다" 처리 (상황에 따라 세분화 필요)
        return ErrorResponse.toResponseEntity(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    // 6. 예측하지 못한 모든 에러 (C001 - 최후의 방어선)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Unhandled Exception: ", e); // 스택 트레이스 출력
        return ErrorResponse.toResponseEntity(ErrorCode.SERVER_ERROR);
    }
}