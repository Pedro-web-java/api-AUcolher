package com.aucolher.api.shared.exception;

/** Exceção para regras de negócio violadas (e-mail duplicado, CNPJ inválido, etc). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
