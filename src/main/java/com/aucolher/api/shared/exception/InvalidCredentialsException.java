package com.aucolher.api.shared.exception;

/** Exceção para falhas de autenticação (login/senha incorretos, conta incompatível, etc). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
