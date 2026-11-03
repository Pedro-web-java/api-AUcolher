package com.aucolher.api.security;

import com.aucolher.api.shared.exception.ErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Conta banida pela moderação (users.active = false). O frontend reconhece a
 * frase e desloga com o aviso — por isso ela é a mesma no login, no login
 * Google e em qualquer requisição feita com um token emitido antes do banimento.
 */
public final class SuspendedAccount {

    public static final String MESSAGE = "Esta conta foi suspensa pela moderação do AUcolher.";

    private SuspendedAccount() {
    }

    /** Responde 403 no formato do ErrorResponseDTO, para quem está fora dos controllers (filtros, OAuth2). */
    public static void write(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        ErrorResponseDTO body = new ErrorResponseDTO(LocalDateTime.now(), HttpServletResponse.SC_FORBIDDEN,
                "Acesso negado", MESSAGE, null);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
