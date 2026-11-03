package com.aucolher.api.report.entity;

import com.aucolher.api.animal.entity.Species;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Denúncia de um anúncio (ANIMAL) ou de um perfil (USER). O alvo é
 * polimórfico (tipo + id, sem chave estrangeira), e as colunas target_*
 * guardam uma cópia dele no momento da denúncia: o admin continua vendo o que
 * foi denunciado mesmo depois de o anúncio sair do ar.
 *
 * Uma denúncia em análise por pessoa e alvo (índice uq_reports_pending).
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // ===================== Cópia do alvo =====================

    @Column(name = "target_name", nullable = false, length = 150)
    private String targetName;

    @Column(name = "target_photo_url", columnDefinition = "TEXT")
    private String targetPhotoUrl;

    /** Só em USER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_user_type", length = 10)
    private UserType targetUserType;

    @Column(name = "target_city", length = 100)
    private String targetCity;

    @Column(name = "target_state", length = 2)
    private String targetState;

    /** Só em ANIMAL, como os três campos do dono abaixo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_species", length = 20)
    private Species targetSpecies;

    /** O dono do anúncio — é por ele que as denúncias se resolvem quando ele é banido. */
    @Column(name = "target_owner_id")
    private Long targetOwnerId;

    @Column(name = "target_owner_name", length = 150)
    private String targetOwnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_owner_user_type", length = 10)
    private UserType targetOwnerUserType;

    // ===================== Denúncia =====================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String description;

    // ===================== Julgamento =====================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportResolution resolution;

    /** O admin que julgou o caso. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onPersist() {
        createdAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

    /** Todas as denúncias de um caso recebem a mesma decisão e o mesmo `now`. */
    public void resolve(ReportResolution decision, User admin, LocalDateTime now) {
        status = ReportStatus.RESOLVED;
        resolution = decision;
        resolvedBy = admin;
        resolvedAt = now;
    }
}
