package com.aucolher.api.adoption.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status de um pedido de adoção:
 * PENDING → ACCEPTED → AWAITING_DELIVERY → CONCLUDED, ou REJECTED pelo dono,
 * ou CANCELLED (por uma das partes ou pelo sistema — o motivo fica em CancelReason).
 * Depois de CONCLUDED, o animal pode voltar a quem doou (RETURNED) ou ser
 * republicado pelo adotante (REHOMED): os dois contam na taxa de reabandono.
 */
public enum AdoptionStatus {
    PENDING,
    ACCEPTED,
    AWAITING_DELIVERY,
    CONCLUDED,
    REJECTED,
    CANCELLED,
    RETURNED,
    REHOMED;

    /** Pedidos ainda em andamento: podem ser aceitos, entregues ou cancelados. */
    public static final Set<AdoptionStatus> ACTIVE = EnumSet.of(PENDING, ACCEPTED, AWAITING_DELIVERY);

    /** Adoções que aconteceram, mesmo que o animal tenha voltado ou sido repassado depois. */
    public static final Set<AdoptionStatus> HAPPENED = EnumSet.of(CONCLUDED, RETURNED, REHOMED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
