package com.aucolher.api.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatRequestLookup {

    private final JdbcTemplate jdbc;

    public ChatRequestLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ChatRequestInfo> find(long requestId) {
        List<ChatRequestInfo> list = jdbc.query("""
                select r.id, r.adopter_id, a.owner_id, r.status, r.cancelled_from_status
                from adoption_requests r
                join animals a on a.id = r.animal_id
                where r.id = ?
                """,
                (rs, i) -> new ChatRequestInfo(rs.getLong("id"), rs.getLong("adopter_id"), rs.getLong("owner_id"),
                        rs.getString("status"), rs.getString("cancelled_from_status")),
                requestId);
        return list.stream().findFirst();
    }
}
