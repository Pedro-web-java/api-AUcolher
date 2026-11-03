package com.aucolher.api.shared.exception;

/** Registro inexistente — ou que o usuário não pode ver, como o anúncio inativo de outra pessoa. Vira 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
