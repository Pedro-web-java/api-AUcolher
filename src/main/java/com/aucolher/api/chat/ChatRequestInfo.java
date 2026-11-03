package com.aucolher.api.chat;

/** O que o chat precisa saber do pedido: as duas partes e o status (como texto, sem depender do enum). */
public record ChatRequestInfo(long id, long adopterId, long ownerId, String status, String cancelledFromStatus) {
}
