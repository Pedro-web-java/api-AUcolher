package com.aucolher.api.shared.exception;

/** 409. Lembre de mapear no seu @RestControllerAdvice, igual ao BusinessException (400). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
