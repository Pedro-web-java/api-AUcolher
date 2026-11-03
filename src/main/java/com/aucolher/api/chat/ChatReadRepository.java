package com.aucolher.api.chat;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatReadRepository extends JpaRepository<ChatRead, ChatReadId> {

    /** Upsert que nunca volta para trás (GREATEST). */
    @Modifying
    @Query(value = """
            INSERT INTO chat_reads (request_id, user_id, last_read_message_id, read_at)
            VALUES (:requestId, :userId, :lastMessageId, :readAt)
            ON CONFLICT (request_id, user_id) DO UPDATE
            SET last_read_message_id = GREATEST(chat_reads.last_read_message_id, EXCLUDED.last_read_message_id),
                read_at = :readAt
            """, nativeQuery = true)
    void upsert(@Param("requestId") Long requestId,
                @Param("userId") Long userId,
                @Param("lastMessageId") Long lastMessageId,
                @Param("readAt") LocalDateTime readAt);

    /** A exclusão de conta anonimiza a linha de users, então o ON DELETE CASCADE não dispara. */
    @Modifying
    @Query("delete from ChatRead r where r.id.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
