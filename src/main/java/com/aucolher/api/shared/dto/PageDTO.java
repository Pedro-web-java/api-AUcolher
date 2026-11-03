package com.aucolher.api.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Formato padrão das respostas paginadas da API.
 *
 * Devolver o Page do Spring direto expõe um JSON grande e que o
 * próprio Spring Data avisa que pode mudar entre versões — este record fixa
 * o contrato com o frontend. `page` começa em 0.
 */
public record PageDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageDTO<T> from(Page<T> page) {
        return new PageDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
