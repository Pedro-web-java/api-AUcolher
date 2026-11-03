package com.aucolher.api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato único de erro da API.
 *
 * `message` é o texto pronto para exibir ao usuário; `errors` traz o detalhe
 * campo a campo (nome do campo do DTO -> motivo) para o frontend destacar os
 * inputs inválidos, e só aparece no JSON quando existe.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> errors
) {}
