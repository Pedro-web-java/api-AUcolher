package com.aucolher.api.admin.dto;

/**
 * A situação do anúncio do ponto de vista da plataforma. IN_PROGRESS não é
 * gravado: é um animal disponível que já tem um pedido aceito ou aguardando
 * entrega.
 */
public enum ListingStatus {
    AVAILABLE,
    IN_PROGRESS,
    ADOPTED,
    INACTIVE,
    REMOVED
}
