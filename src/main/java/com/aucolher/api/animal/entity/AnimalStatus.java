package com.aucolher.api.animal.entity;

/**
 * Situação do anúncio.
 *
 * INACTIVE é a exclusão lógica: o dono tirou o anúncio do ar, mas o registro
 * fica para o histórico (favoritos, futuras adoções). "Em processo" não é um
 * status gravado — vai ser derivado dos pedidos de adoção.
 */
public enum AnimalStatus {
    AVAILABLE,
    ADOPTED,
    INACTIVE
}
