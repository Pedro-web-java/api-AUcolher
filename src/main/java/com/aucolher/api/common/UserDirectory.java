package com.aucolher.api.common;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Consultas mínimas à tabela users, para os módulos não dependerem da entidade User. */
@Component
public class UserDirectory {

    private final JdbcTemplate jdbc;

    public UserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findIdByEmail(String email) {
        List<Long> ids = jdbc.query("select id from users where lower(email) = lower(?)",
                (rs, i) -> rs.getLong(1), email);
        return ids.stream().findFirst();
    }

    public boolean isActiveNgo(long userId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from users where id = ? and user_type = 'NGO' and active = true",
                Integer.class, userId);
        return n != null && n > 0;
    }
}
