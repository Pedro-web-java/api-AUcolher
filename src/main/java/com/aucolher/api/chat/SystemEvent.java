package com.aucolher.api.chat;

/** O banco guarda o código; o front monta a frase em português com o nome do animal. */
public enum SystemEvent {
    DELIVERY_REQUESTED,   // ACCEPTED -> AWAITING_DELIVERY
    DELIVERY_DECLINED,    // adotante: "Ainda não recebi" (AWAITING_DELIVERY -> ACCEPTED)
    ADOPTION_CONCLUDED,   // AWAITING_DELIVERY -> CONCLUDED
    ADOPTER_WITHDREW,     // adotante cancela a partir de ACCEPTED ou AWAITING_DELIVERY
    OWNER_WITHDREW,       // dono cancela (ACCEPTED ou AWAITING_DELIVERY)
    ANIMAL_RETURNED,      // CONCLUDED -> RETURNED
    ANIMAL_REHOMED        // CONCLUDED -> REHOMED
}
