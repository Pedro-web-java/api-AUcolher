package com.aucolher.api.campaign.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ngo_id", nullable = false)
    private Long ngoId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignCategory category;

    @Column(name = "custom_category", length = 30)
    private String customCategory;

    @Column(name = "is_urgent", nullable = false)
    private boolean urgent;

    @Column(name = "goal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal goalAmount;

    private LocalDate deadline;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.ACTIVE;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "removed_by_moderation", nullable = false)
    private boolean removedByModeration;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderated_by")
    private Long moderatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Encerrada = status CLOSED, ou prazo vencido (calculado na leitura, sem job agendado). */
    public boolean isClosed() {
        return status == CampaignStatus.CLOSED || deadlinePassed();
    }

    public boolean deadlinePassed() {
        return deadline != null && deadline.isBefore(LocalDate.now());
    }

    /** O admin editou a campanha de uma ONG: fica registrado quando e quem. */
    public void markModeratedBy(Long adminId) {
        moderatedAt = LocalDateTime.now();
        moderatedBy = adminId;
    }
}
