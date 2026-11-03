package com.aucolher.api.common;

/** Mesmo formato do ErrorResponseDTO do projeto: { status, error, message }. */
public record ApiErrorBody(int status, String error, String message) {
}
