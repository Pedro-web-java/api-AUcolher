package com.aucolher.api.adoption.entity;

/** Por que uma adoção não deu certo: o motivo de um retorno ou de um repasse. */
public enum ReturnReason {
    BEHAVIOR,
    OTHER_PETS,
    CHILDREN,
    HOUSING_OR_ROUTINE,
    COSTS,
    ANIMAL_HEALTH,
    /** Exige a observação. */
    OTHER
}
