package com.aucolher.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.aucolher.api.chat.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aucolher.api.chat.dto.ChatMessageDTO;
import com.aucolher.api.chat.dto.ChatSummaryDTO;
import com.aucolher.api.chat.dto.ConversationSummaryDTO;
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

    @Test
    void returnedAndRehomedKeepTheConversationReadOnly() {
        request("RETURNED", null);
        service.listMessages(OWNER, 8L, null);
        assertEquals("Esta adoção foi concluída: o chat está fechado para novas mensagens.",
                assertThrows(ApiException.class, () -> service.send(OWNER, 8L, "oi")).getMessage());
        request("REHOMED", null);
        service.listMessages(ADOPTER, 8L, null);
        assertEquals("Esta adoção foi concluída: o chat está fechado para novas mensagens.",
                assertThrows(ApiException.class, () -> service.send(ADOPTER, 8L, "oi")).getMessage());
    }

    @Test
    void exactlyTheLimitIsAccepted() {
        request("ACCEPTED", null);
        assertEquals(1000, service.send(ADOPTER, 8L, "a".repeat(1000)).text().length());
    }

    @Test
    void listingWithAfterBringsOnlyTheNewOnes() {
        request("ACCEPTED", null);
        service.listMessages(ADOPTER, 8L, 57L);
        verify(messages).findByRequestIdAndIdGreaterThanOrderByIdAsc(8L, 57L);
        verify(messages, never()).findByRequestIdOrderByIdAsc(any());
    }

    @Test
    void summaryPutsTheLatestFirstAndAddsUpTheUnread() {
        ChatMessage last = mock(ChatMessage.class);
        when(last.getId()).thenReturn(58L);
        ChatMessage older = mock(ChatMessage.class);
        when(older.getId()).thenReturn(20L);
        List<ChatMessageRepository.ConversationRow> rows =
                List.of(row(5L, 0L, null), row(9L, 1L, 20L), row(6L, 0L, null), row(8L, 2L, 58L));
        when(messages.summarize(ADOPTER)).thenReturn(rows);
        when(messages.findAllById(any())).thenReturn(List.of(older, last));

        ChatSummaryDTO summary = service.summary(ADOPTER);

        assertEquals(3, summary.totalUnread());
        assertEquals(List.of(8L, 9L, 6L, 5L), summary.conversations().stream().map(ConversationSummaryDTO::requestId).toList());
        assertEquals(58L, summary.conversations().get(0).lastMessage().id());
        assertNull(summary.conversations().get(3).lastMessage());
    }

    private static ChatMessageRepository.ConversationRow row(Long requestId, Long unread, Long lastMessageId) {
        ChatMessageRepository.ConversationRow row = mock(ChatMessageRepository.ConversationRow.class);
        when(row.getRequestId()).thenReturn(requestId);
        when(row.getUnreadCount()).thenReturn(unread);
        when(row.getLastMessageId()).thenReturn(lastMessageId);
        return row;
    }
}
