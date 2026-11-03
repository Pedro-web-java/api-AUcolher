package com.aucolher.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.aucolher.api.chat.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aucolher.api.chat.dto.ChatMessageDTO;
import com.aucolher.api.common.ApiException;

class ChatServiceTest {

    private static final long ADOPTER = 4L;
    private static final long OWNER = 9L;

    private ChatRequestLookup requests;
    private ChatMessageRepository messages;
    private ChatReadRepository reads;
    private ChatService service;

    @BeforeEach
    void setUp() {
        requests = mock(ChatRequestLookup.class);
        messages = mock(ChatMessageRepository.class);
        reads = mock(ChatReadRepository.class);
        when(messages.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));
        service = new ChatService(requests, messages, reads);
    }

    private void request(String status, String cancelledFrom) {
        when(requests.find(8L)).thenReturn(Optional.of(new ChatRequestInfo(8L, ADOPTER, OWNER, status, cancelledFrom)));
    }

    private static int statusOf(Runnable r) {
        return assertThrows(ApiException.class, r::run).getStatus().value();
    }

    @Test
    void missingRequestIs404() {
        when(requests.find(8L)).thenReturn(Optional.empty());
        assertEquals(404, statusOf(() -> service.listMessages(ADOPTER, 8L, null)));
    }

    @Test
    void outsiderIs403() {
        request("ACCEPTED", null);
        assertEquals(403, statusOf(() -> service.listMessages(77L, 8L, null)));
    }

    @Test
    void pendingRejectedAndCancelledBeforeAcceptHaveNoConversation() {
        request("PENDING", null);
        assertEquals(404, statusOf(() -> service.listMessages(ADOPTER, 8L, null)));
        request("REJECTED", null);
        assertEquals(404, statusOf(() -> service.listMessages(ADOPTER, 8L, null)));
        request("CANCELLED", "PENDING");
        assertEquals(404, statusOf(() -> service.listMessages(ADOPTER, 8L, null)));
    }

    @Test
    void cancelledAfterAcceptKeepsTheConversationReadOnly() {
        request("CANCELLED", "ACCEPTED");
        service.listMessages(OWNER, 8L, null);
        ApiException e = assertThrows(ApiException.class, () -> service.send(OWNER, 8L, "oi"));
        assertEquals("Este pedido foi cancelado: o chat está fechado para novas mensagens.", e.getMessage());
    }

    @Test
    void sendTrimsAndSavesInAccepted() {
        request("ACCEPTED", null);
        ChatMessageDTO dto = service.send(ADOPTER, 8L, "  Oi!\nPosso visitar?  ");
        assertEquals("Oi!\nPosso visitar?", dto.text());
        assertEquals(MessageType.USER, dto.type());
        assertEquals(ADOPTER, dto.senderId());
    }

    @Test
    void emptyAndTooLongTextsAreRefused() {
        request("ACCEPTED", null);
        assertEquals("Escreva uma mensagem.",
                assertThrows(ApiException.class, () -> service.send(ADOPTER, 8L, "   ")).getMessage());
        assertEquals("A mensagem pode ter no máximo 1000 caracteres.",
                assertThrows(ApiException.class, () -> service.send(ADOPTER, 8L, "a".repeat(1001))).getMessage());
        verify(messages, never()).save(any());
    }

    @Test
    void awaitingDeliveryAndConcludedAreClosedForSending() {
        request("AWAITING_DELIVERY", null);
        assertEquals("A entrega já foi confirmada: o chat está fechado para novas mensagens.",
                assertThrows(ApiException.class, () -> service.send(OWNER, 8L, "oi")).getMessage());
        request("CONCLUDED", null);
        assertEquals("Esta adoção foi concluída: o chat está fechado para novas mensagens.",
                assertThrows(ApiException.class, () -> service.send(OWNER, 8L, "oi")).getMessage());
    }

    @Test
    void markReadValidatesAndUpserts() {
        request("CONCLUDED", null);
        ApiException e = assertThrows(ApiException.class, () -> service.markRead(ADOPTER, 8L, null));
        assertEquals("Informe a última mensagem lida.", e.getMessage());
        service.markRead(ADOPTER, 8L, 58L);
        verify(reads).upsert(any(), any(), any(), any());
    }

    @Test
    void systemMessageIsStored() {
        service.addSystemMessage(8L, SystemEvent.DELIVERY_REQUESTED);
        verify(messages).save(any(ChatMessage.class));
    }
}
