package com.aucolher.api.shared.exception;

/** O pedido bate com algo que já existe e ainda vale (ex: uma denúncia em análise). Vira 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
