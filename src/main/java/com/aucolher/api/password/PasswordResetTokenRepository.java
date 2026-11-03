package com.aucolher.api.password;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    /** Pedir um link novo invalida os anteriores ainda não usados. */
    @Modifying(clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateOpen(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff or (t.usedAt is not null and t.usedAt < :cutoff)")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** A exclusão de conta anonimiza a linha de users, então o ON DELETE CASCADE não dispara. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
