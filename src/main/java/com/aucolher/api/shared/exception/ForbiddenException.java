package com.aucolher.api.shared.exception;

/** Usuário autenticado tentando alterar algo que não é dele (ex: o animal de outra conta). Vira 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
