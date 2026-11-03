package com.aucolher.api.adoption.entity;

/** Por que um pedido foi cancelado. */
public enum CancelReason {
    /** O adotante desistiu. */
    ADOPTER_WITHDREW,
    /** O dono desistiu depois de aceitar. */
    OWNER_WITHDREW,
    /** Outro pedido do mesmo animal foi concluído. */
    ANIMAL_ADOPTED,
    /** O dono tirou o animal do ar, marcou como adotado por fora ou excluiu o anúncio. */
    ANIMAL_UNAVAILABLE,
    /** Uma das partes excluiu a própria conta. */
    ACCOUNT_DELETED,
    /** Uma das partes foi banida pela moderação. */
    ACCOUNT_BANNED,
    /** O anúncio foi removido pela moderação. */
    LISTING_REMOVED
}
