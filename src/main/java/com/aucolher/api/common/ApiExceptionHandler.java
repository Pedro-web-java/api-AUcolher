package com.aucolher.api.common;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte ApiException no corpo { status, error, message }.
 * Se preferir, apague esta classe e trate ApiException no seu GlobalExceptionHandler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorBody> handle(ApiException ex) {
        var status = ex.getStatus();
        return ResponseEntity.status(status)
                .body(new ApiErrorBody(status.value(), status.getReasonPhrase(), ex.getMessage()));
    }
}
