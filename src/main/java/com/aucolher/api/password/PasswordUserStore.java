package com.aucolher.api.password;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Acesso à tabela users só para o que a recuperação de senha precisa. */
@Component
public class PasswordUserStore {

    public record UserRow(long id, String name, String email, String password, boolean active) {
    }

    private static final RowMapper<UserRow> MAPPER = (rs, i) -> new UserRow(
            rs.getLong("id"), rs.getString("name"), rs.getString("email"),
            rs.getString("password"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public PasswordUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        List<UserRow> list = jdbc.query(
                "select id, name, email, password, active from users where lower(email) = lower(?)", MAPPER, email);
        return list.stream().findFirst();
    }

    public Optional<UserRow> findById(long id) {
        List<UserRow> list = jdbc.query(
                "select id, name, email, password, active from users where id = ?", MAPPER, id);
        return list.stream().findFirst();
    }

    /** Grava o hash novo e o momento da troca (derruba os JWT emitidos antes). Só conta ativa. */
    public boolean updatePassword(long id, String passwordHash, LocalDateTime changedAt) {
        return jdbc.update(
                "update users set password = ?, password_changed_at = ? where id = ? and active = true",
                passwordHash, Timestamp.valueOf(changedAt), id) > 0;
    }
}
