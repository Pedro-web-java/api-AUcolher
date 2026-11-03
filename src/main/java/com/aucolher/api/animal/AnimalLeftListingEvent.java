package com.aucolher.api.animal;

/**
 * O animal saiu da vitrine: o dono marcou como adotado fora da plataforma,
 * tirou do ar (INACTIVE) ou excluiu o anúncio — ou a moderação o removeu
 * (byModeration, os pedidos são cancelados com outro motivo).
 *
 * Publicado dentro da transação da mudança de status — quem escuta (os
 * pedidos de adoção, que cancelam os ativos) roda junto e cai junto se der erro.
 */
public record AnimalLeftListingEvent(Long animalId, boolean byModeration) {

    /** Saída pelo próprio dono. */
    public AnimalLeftListingEvent(Long animalId) {
        this(animalId, false);
    }
}
