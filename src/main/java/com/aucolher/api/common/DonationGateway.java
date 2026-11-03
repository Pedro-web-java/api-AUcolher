package com.aucolher.api.common;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Acesso à tabela donations usado pela conexão Mercado Pago e pelo webhook. */
@Component
public class DonationGateway {

    public record DonationRef(long id, long ngoId, String status) {
    }

    private final JdbcTemplate jdbc;

    public DonationGateway(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Há PIX pendente e ainda não vencido numa campanha da ONG? */
    public boolean hasPendingDonations(long ngoId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from donations where ngo_id = ? and status = 'PENDING' and expires_at > ?",
                Integer.class, ngoId, Timestamp.valueOf(LocalDateTime.now()));
        return n != null && n > 0;
    }

    public Optional<DonationRef> findByMpOrderId(String orderId) {
        List<DonationRef> list = jdbc.query(
                "select id, ngo_id, status from donations where mp_order_id = ?",
                (rs, i) -> new DonationRef(rs.getLong("id"), rs.getLong("ngo_id"), rs.getString("status")),
                orderId);
        return list.stream().findFirst();
    }

    /** UPDATE atômico: webhook e polling nunca somam duas vezes. */
    public boolean approve(long donationId) {
        return jdbc.update(
                "update donations set status = 'APPROVED', approved_at = ? "
                        + "where id = ? and status in ('PENDING', 'CANCELLED')",
                Timestamp.valueOf(LocalDateTime.now()), donationId) > 0;
    }

    public boolean markExpired(long donationId) {
        return jdbc.update("update donations set status = 'EXPIRED' where id = ? and status = 'PENDING'",
                donationId) > 0;
    }

    public boolean markCancelled(long donationId) {
        return jdbc.update("update donations set status = 'CANCELLED' where id = ? and status = 'PENDING'",
                donationId) > 0;
    }
}
