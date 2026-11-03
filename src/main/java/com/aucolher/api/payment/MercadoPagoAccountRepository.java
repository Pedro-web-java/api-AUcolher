package com.aucolher.api.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MercadoPagoAccountRepository extends JpaRepository<MercadoPagoAccount, Long> {

    Optional<MercadoPagoAccount> findByMpUserId(Long mpUserId);

    /** Trava a linha: o refresh_token muda a cada renovação, duas renovações juntas perderiam o acesso. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MercadoPagoAccount a where a.ngoId = :ngoId")
    Optional<MercadoPagoAccount> findForUpdate(@Param("ngoId") Long ngoId);

    List<MercadoPagoAccount> findByStatusAndExpiresAtBefore(MercadoPagoAccountStatus status, LocalDateTime limit);
}
