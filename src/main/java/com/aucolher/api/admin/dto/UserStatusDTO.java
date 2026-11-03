package com.aucolher.api.admin.dto;

/**
 * Corpo do PATCH /api/admin/users/{id}/status: `{ "active": false }` bane a
 * conta e `{ "active": true }` reativa. A ausência do campo é conferida na
 * Service, com a frase do contrato.
 */
public record UserStatusDTO(Boolean active) {
}
