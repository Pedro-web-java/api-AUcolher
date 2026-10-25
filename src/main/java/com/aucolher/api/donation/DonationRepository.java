package com.aucolher.api.donation;

import com.aucolher.api.donation.entity.Donation;
import com.aucolher.api.donation.entity.DonationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    /** Linhas [campaignId, soma, quantidade] das doações APPROVED (raisedAmount / donationsCount). */
    @Query("select d.campaignId, coalesce(sum(d.amount), 0), count(d) from Donation d "
            + "where d.campaignId in :ids "
            + "and d.status = com.aucolher.api.donation.entity.DonationStatus.APPROVED "
            + "group by d.campaignId")
    List<Object[]> totalsByCampaignIds(@Param("ids") Collection<Long> ids);

    long countByCampaignIdAndStatus(Long campaignId, DonationStatus status);

    /** Encerrar/excluir campanha: cancela os PIX ainda pendentes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Donation d set d.status = com.aucolher.api.donation.entity.DonationStatus.CANCELLED "
            + "where d.campaignId = :campaignId "
            + "and d.status = com.aucolher.api.donation.entity.DonationStatus.PENDING")
    int cancelPendingByCampaign(@Param("campaignId") Long campaignId);

    /**
     * Aprovação atômica: webhook e polling podem chegar juntos, mas só um deles muda a linha
     * (devolve 1). Aceita CANCELLED: pagamento que chega depois do cancelamento vale mesmo assim.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Donation d set d.status = com.aucolher.api.donation.entity.DonationStatus.APPROVED, "
            + "d.approvedAt = :now where d.id = :id and d.status in ("
            + "com.aucolher.api.donation.entity.DonationStatus.PENDING, "
            + "com.aucolher.api.donation.entity.DonationStatus.CANCELLED)")
    int approve(@Param("id") Long id, @Param("now") LocalDateTime now);

    Optional<Donation> findByMpOrderId(String mpOrderId);

    List<Donation> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    List<Donation> findByNgoIdAndStatusOrderByApprovedAtDesc(Long ngoId, DonationStatus status);
}
