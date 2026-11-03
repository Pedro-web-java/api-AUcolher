package com.aucolher.api.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aucolher.api.chat.dto.ChatMessageDTO;
import com.aucolher.api.chat.dto.ChatSummaryDTO;
import com.aucolher.api.chat.dto.ConversationSummaryDTO;
import com.aucolher.api.common.ApiException;

/**
 * O chat é o pedido de adoção: o id da conversa é adoption_requests.id.
 * Só adotante e dono leem e escrevem; só se escreve em ACCEPTED.
 */
@Service
public class ChatService {

    static final int MAX_LENGTH = 1000;
    private static final String NOT_FOUND = "Conversa não encontrada.";

    /** Status que têm conversa. RETURNED/REHOMED vêm depois de CONCLUDED, então a conversa continua visível. */
    private static final Set<String> WITH_CONVERSATION =
            Set.of("ACCEPTED", "AWAITING_DELIVERY", "CONCLUDED", "RETURNED", "REHOMED");
    private static final Set<String> CANCELLED_AFTER_ACCEPT = Set.of("ACCEPTED", "AWAITING_DELIVERY");

    private final ChatRequestLookup requests;
    private final ChatMessageRepository messages;
    private final ChatReadRepository reads;

    public ChatService(ChatRequestLookup requests, ChatMessageRepository messages, ChatReadRepository reads) {
        this.requests = requests;
        this.messages = messages;
        this.reads = reads;
    }

    // ------------------------------------------------------------------ rotas

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> listMessages(long userId, long requestId, Long after) {
        loadAccessible(userId, requestId);
        List<ChatMessage> list = after == null
                ? messages.findByRequestIdOrderByIdAsc(requestId)
                : messages.findByRequestIdAndIdGreaterThanOrderByIdAsc(requestId, after);
        return list.stream().map(ChatMessageDTO::from).toList();
    }

    @Transactional
    public ChatMessageDTO send(long userId, long requestId, String rawText) {
        ChatRequestInfo info = loadAccessible(userId, requestId);
        if (!"ACCEPTED".equals(info.status())) {
            throw ApiException.badRequest(closedMessage(info));
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Escreva uma mensagem.");
        }
        if (text.length() > MAX_LENGTH) {
            throw ApiException.badRequest("A mensagem pode ter no máximo 1000 caracteres.");
        }
        return ChatMessageDTO.from(messages.save(ChatMessage.fromUser(requestId, userId, text)));
    }

    /** Vale em qualquer status com conversa (inclusive travada). A marca só anda para frente. */
    @Transactional
    public void markRead(long userId, long requestId, Long lastMessageId) {
        loadAccessible(userId, requestId);
        if (lastMessageId == null || lastMessageId < 0) {
            throw ApiException.badRequest("Informe a última mensagem lida.");
        }
        reads.upsert(requestId, userId, lastMessageId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public ChatSummaryDTO summary(long userId) {
        List<ChatMessageRepository.ConversationRow> rows = new ArrayList<>(messages.summarize(userId));
        // a mensagem mais recente primeiro; sem mensagem no fim, com o requestId mais novo primeiro
        rows.sort((a, b) -> {
            Long la = a.getLastMessageId();
            Long lb = b.getLastMessageId();
            if (la != null && lb != null) return Long.compare(lb, la);
            if (la != null) return -1;
            if (lb != null) return 1;
            return Long.compare(b.getRequestId(), a.getRequestId());
        });

        List<Long> lastIds = rows.stream().map(ChatMessageRepository.ConversationRow::getLastMessageId)
                .filter(id -> id != null).toList();
        Map<Long, ChatMessage> lastById = messages.findAllById(lastIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        long total = 0;
        List<ConversationSummaryDTO> conversations = new ArrayList<>();
        for (ChatMessageRepository.ConversationRow row : rows) {
            long unread = row.getUnreadCount() == null ? 0 : row.getUnreadCount();
            total += unread;
            ChatMessage last = row.getLastMessageId() == null ? null : lastById.get(row.getLastMessageId());
            conversations.add(new ConversationSummaryDTO(row.getRequestId(), unread,
                    last == null ? null : ChatMessageDTO.from(last)));
        }
        return new ChatSummaryDTO(total, conversations);
    }

    // ------------------------------------------------------------------ mensagens de sistema

    /**
     * Chamado pelo AdoptionRequestService na MESMA @Transactional da troca de status: o aviso
     * nunca existe sem a troca (nem o contrário).
     */
    @Transactional
    public void addSystemMessage(long requestId, SystemEvent event) {
        messages.save(ChatMessage.system(requestId, event));
    }

    // ------------------------------------------------------------------ regras

    /** Ordem: 404 pedido inexistente, 403 não é parte, 404 nunca teve conversa. */
    private ChatRequestInfo loadAccessible(long userId, long requestId) {
        ChatRequestInfo info = requests.find(requestId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (info.adopterId() != userId && info.ownerId() != userId) {
            throw ApiException.forbidden("Você não participa desta conversa.");
        }
        if (!hadConversation(info)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return info;
    }

    static boolean hadConversation(ChatRequestInfo info) {
        if (WITH_CONVERSATION.contains(info.status())) {
            return true;
        }
        return "CANCELLED".equals(info.status())
                && info.cancelledFromStatus() != null
                && CANCELLED_AFTER_ACCEPT.contains(info.cancelledFromStatus());
    }

    private static String closedMessage(ChatRequestInfo info) {
        return switch (info.status()) {
            case "AWAITING_DELIVERY" -> "A entrega já foi confirmada: o chat está fechado para novas mensagens.";
            case "CANCELLED" -> "Este pedido foi cancelado: o chat está fechado para novas mensagens.";
            default -> "Esta adoção foi concluída: o chat está fechado para novas mensagens.";
        };
    }
}
