package com.aucolher.api.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRequestIdOrderByIdAsc(Long requestId);

    List<ChatMessage> findByRequestIdAndIdGreaterThanOrderByIdAsc(Long requestId, Long after);

    interface ConversationRow {
        Long getRequestId();

        Long getUnreadCount();

        Long getLastMessageId();
    }

    /**
     * Todas as conversas da conta (como dona ou adotante), inclusive sem mensagem e travadas.
     * Não lida = id maior que o já lido e não escrita pela própria pessoa (as de sistema contam).
     */
    @Query(value = """
            SELECT r.id AS "requestId",
                   COUNT(m.id) FILTER (WHERE m.id > COALESCE(cr.last_read_message_id, 0)
                                         AND (m.sender_id IS NULL OR m.sender_id <> :me)) AS "unreadCount",
                   MAX(m.id) AS "lastMessageId"
            FROM adoption_requests r
            JOIN animals a ON a.id = r.animal_id
            LEFT JOIN chat_reads cr ON cr.request_id = r.id AND cr.user_id = :me
            LEFT JOIN chat_messages m ON m.request_id = r.id
            WHERE (r.adopter_id = :me OR a.owner_id = :me)
              AND (r.status IN ('ACCEPTED', 'AWAITING_DELIVERY', 'CONCLUDED', 'RETURNED', 'REHOMED')
                   OR (r.status = 'CANCELLED' AND r.cancelled_from_status IN ('ACCEPTED', 'AWAITING_DELIVERY')))
            GROUP BY r.id
            """, nativeQuery = true)
    List<ConversationRow> summarize(@Param("me") Long me);
}
