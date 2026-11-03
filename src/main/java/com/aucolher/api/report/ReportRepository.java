package com.aucolher.api.report;

import com.aucolher.api.report.entity.Report;
import com.aucolher.api.report.entity.ReportStatus;
import com.aucolher.api.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Denúncias. As listagens trazem quem denunciou junto (@EntityGraph). */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** "Minhas denúncias", da mais recente para a mais antiga. */
    List<Report> findByReporterIdOrderByCreatedAtDescIdDesc(Long reporterId);

    /** Painel do admin: todas, em qualquer situação. */
    @EntityGraph(attributePaths = "reporter")
    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    /** A denúncia em análise que impede outra da mesma pessoa para o mesmo alvo. */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, ReportTargetType targetType,
                                                                Long targetId, ReportStatus status);

    /** Um caso: as denúncias de um alvo numa situação. */
    @EntityGraph(attributePaths = "reporter")
    List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    /**
     * Tudo o que uma conta banida deixa pendente: as denúncias contra o perfil
     * dela e contra os anúncios dela.
     */
    @EntityGraph(attributePaths = "reporter")
    @Query("SELECT r FROM Report r WHERE r.status = :status AND ("
            + "(r.targetType = com.aucolher.api.report.entity.ReportTargetType.USER AND r.targetId = :userId)"
            + " OR (r.targetType = com.aucolher.api.report.entity.ReportTargetType.ANIMAL AND r.targetOwnerId = :userId))")
    List<Report> findAgainstAccount(Long userId, ReportStatus status);

    long countByStatus(ReportStatus status);

    /** Quantos alvos diferentes as denúncias pendentes atingem ("casos" esperando decisão). */
    @Query(value = "SELECT count(*) FROM (SELECT DISTINCT target_type, target_id FROM reports WHERE status = 'PENDING') cases",
            nativeQuery = true)
    long countPendingTargets();
}
