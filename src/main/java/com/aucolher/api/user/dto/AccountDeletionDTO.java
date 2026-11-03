package com.aucolher.api.user.dto;

/**
 * Corpo do POST /api/users/me/deletion. Conta com senha manda
 * `{ "password": "..." }`; conta do Google sem senha manda
 * `{ "confirmation": "EXCLUIR" }`. A conferência fica na Service, com as
 * frases do contrato.
 */
public record AccountDeletionDTO(String password, String confirmation) {
}
