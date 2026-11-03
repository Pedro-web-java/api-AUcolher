package com.aucolher.api.report.entity;

/** A decisão do admin sobre um caso (o alvo, com todas as denúncias pendentes dele). */
public enum ReportResolution {
    /** O anúncio denunciado foi removido. */
    LISTING_REMOVED,
    /** A conta denunciada (ou a dona do anúncio denunciado) foi banida. */
    ACCOUNT_BANNED,
    /** Arquivada como improcedente. */
    DISMISSED
}
