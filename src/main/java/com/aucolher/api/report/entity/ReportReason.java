package com.aucolher.api.report.entity;

/** Motivo da denúncia. FAKE_PROFILE só vale para perfil; OTHER exige a descrição. */
public enum ReportReason {
    FAKE_PROFILE,
    SCAM,
    ANIMAL_ABUSE,
    IMPROPER_CONTENT,
    SPAM,
    OTHER;

    public boolean appliesTo(ReportTargetType targetType) {
        return this != FAKE_PROFILE || targetType == ReportTargetType.USER;
    }
}
