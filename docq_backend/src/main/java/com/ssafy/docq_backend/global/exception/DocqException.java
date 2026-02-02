package com.ssafy.docq_backend.global.exception;

import lombok.Getter;

@Getter
public class DocqException extends RuntimeException {

    private final ErrorCode errorCode;

    public DocqException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public DocqException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

}