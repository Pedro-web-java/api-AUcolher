package com.aucolher.api.campaign;

import com.aucolher.api.campaign.entity.Campaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // Públicas: nunca excluídas, nunca de ONG banida.
    String PUBLIC = "c.deleted = false and c.ngoId in (select u.id from User u where u.active = true) ";
    // O prazo vencido é calculado na leitura (sem job): ACTIVE com deadline < hoje já conta como encerrada.
    String ACTIVE_NOW = "c.status = com.aucolher.api.campaign.entity.CampaignStatus.ACTIVE "
            + "and (c.deadline is null or c.deadline >= :today) ";
    String CLOSED_NOW = "(c.status = com.aucolher.api.campaign.entity.CampaignStatus.CLOSED "
            + "or (c.deadline is not null and c.deadline < :today)) ";

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") Long id);

    @Query("select c from Campaign c where " + PUBLIC + "and " + ACTIVE_NOW + "order by c.createdAt desc")
    List<Campaign> findPublicActive(@Param("today") LocalDate today);

    @Query("select c from Campaign c where " + PUBLIC + "and c.ngoId = :ngoId and " + ACTIVE_NOW
            + "order by c.createdAt desc")
    List<Campaign> findPublicActiveByNgo(@Param("ngoId") Long ngoId, @Param("today") LocalDate today);

    @Query("select c from Campaign c where " + PUBLIC + "and " + CLOSED_NOW + "order by c.createdAt desc")
    List<Campaign> findPublicClosed(@Param("today") LocalDate today);

    @Query("select c from Campaign c where " + PUBLIC + "and c.ngoId = :ngoId and " + CLOSED_NOW
            + "order by c.createdAt desc")
    List<Campaign> findPublicClosedByNgo(@Param("ngoId") Long ngoId, @Param("today") LocalDate today);

    /** "Minhas campanhas": ativas e encerradas da ONG, sem as excluídas. */
    List<Campaign> findByNgoIdAndDeletedFalseOrderByCreatedAtDesc(Long ngoId);

    /** Outra campanha urgente ativa da mesma ONG (excludeId = 0 na criação). */
    @Query("select c from Campaign c where c.ngoId = :ngoId and c.urgent = true and c.deleted = false "
            + "and c.id <> :excludeId and " + ACTIVE_NOW)
    List<Campaign> findOtherActiveUrgent(@Param("ngoId") Long ngoId, @Param("excludeId") Long excludeId,
                                         @Param("today") LocalDate today);
}
