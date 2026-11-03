package com.aucolher.api.animal.entity;

/**
 * Situação do anúncio.
 *
 * INACTIVE é a exclusão lógica: o dono tirou o anúncio do ar, mas o registro
 * fica para o histórico (favoritos, futuras adoções). REMOVED é a remoção pela
 * moderação: o dono ainda vê o anúncio, mas não muda mais nada nele. "Em
 * processo" não é um status gravado — é derivado dos pedidos de adoção.
 */
public enum AnimalStatus {
    AVAILABLE,
    ADOPTED,
    INACTIVE,
    REMOVED
}
