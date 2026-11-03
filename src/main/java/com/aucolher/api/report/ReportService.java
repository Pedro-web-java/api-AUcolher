package com.aucolher.api.report;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.report.dto.NewReportDTO;
import com.aucolher.api.report.dto.ReportDTO;
import com.aucolher.api.report.entity.*;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ConflictException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Denúncias do site (anúncios e perfis) e o registro das decisões do admin.
 * As ações de moderação que mexem em vários módulos (banir, remover) ficam na
 * ModerationService, que usa os métodos de resolução daqui.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final int MAX_DESCRIPTION = 500;

    private final ReportRepository reportRepository;
    private final AnimalRepository animalRepository;
    private final UserRepository userRepository;

    /**
     * Pessoa ou ONG denuncia um anúncio ou um perfil. A denúncia guarda uma
     * cópia do alvo. Uma em análise por pessoa e alvo: a segunda responde 409
     * (o índice uq_reports_pending segura também dois cliques ao mesmo tempo).
     */
    @Transactional
    public ReportDTO create(String email, NewReportDTO body) {
        User reporter = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        if (reporter.getUserType() == UserType.ADMIN) {
            throw new ForbiddenException("Contas de admin não fazem denúncias: a moderação é feita pelo painel.");
        }
        requireValidBody(body);

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(body.targetType());
        report.setTargetId(body.targetId());
        report.setReason(body.reason());
        report.setDescription(body.description());
        if (body.targetType() == ReportTargetType.ANIMAL) {
            copyAnimal(report, reporter);
        } else {
            copyUser(report, reporter);
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporter.getId(), body.targetType(), body.targetId(), ReportStatus.PENDING)) {
            throw duplicate(body.targetType());
        }
        try {
            return ReportDTO.mine(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException e) {
            throw duplicate(body.targetType());
        }
    }

    /** As denúncias que a conta fez, das mais recentes para as mais antigas. */
    @Transactional(readOnly = true)
    public List<ReportDTO> listMine(String email) {
        User reporter = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        return reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(reporter.getId()).stream()
                .map(ReportDTO::mine).toList();
    }

    /** Painel do admin: todas as denúncias, com quem denunciou. */
    @Transactional(readOnly = true)
    public List<ReportDTO> listAllForAdmin() {
        return reportRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(ReportDTO::forAdmin).toList();
    }

    // ===================== Decisões do admin (usadas pela ModerationService) =====================

    public Report findReport(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Denúncia não encontrada."));
    }

    /** Julga o caso inteiro: todas as denúncias pendentes do alvo, com a mesma decisão e o mesmo `now`. */
    public List<Report> resolveCase(ReportTargetType targetType, Long targetId, ReportResolution resolution,
                                    User admin, LocalDateTime now) {
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING);
        pending.forEach(report -> report.resolve(resolution, admin, now));
        return pending;
    }

    /**
     * Resolve as pendentes contra o perfil da conta e contra os anúncios dela:
     * ACCOUNT_BANNED no banimento; DISMISSED, sem admin, quando a própria
     * conta é excluída (o conteúdo já saiu do ar).
     */
    public List<Report> resolveAgainstAccount(Long userId, ReportResolution resolution, User admin, LocalDateTime now) {
        List<Report> pending = reportRepository.findAgainstAccount(userId, ReportStatus.PENDING);
        pending.forEach(report -> report.resolve(resolution, admin, now));
        return pending;
    }

    // ===================== Regras do envio =====================

    private static void requireValidBody(NewReportDTO body) {
        if (body.targetType() == null || body.targetId() == null) {
            throw new BusinessException("Informe o que está sendo denunciado.");
        }
        if (body.reason() == null || !body.reason().appliesTo(body.targetType())) {
            throw new BusinessException("Escolha o motivo da denúncia.");
        }
        if (body.description() != null && body.description().length() > MAX_DESCRIPTION) {
            throw new BusinessException("A descrição pode ter no máximo 500 caracteres.");
        }
        if (body.reason() == ReportReason.OTHER && body.description() == null) {
            throw new BusinessException("Conte o que aconteceu: a descrição é obrigatória em \"Outro motivo\".");
        }
    }

    /** O anúncio precisa estar visível no site; a cópia leva a capa, a espécie e o dono. */
    private void copyAnimal(Report report, User reporter) {
        Animal animal = animalRepository.findById(report.getTargetId())
                .filter(found -> found.getStatus() != AnimalStatus.INACTIVE && found.getStatus() != AnimalStatus.REMOVED)
                .filter(found -> Boolean.TRUE.equals(found.getOwner().getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Anúncio não encontrado."));
        User owner = animal.getOwner();
        if (owner.getId().equals(reporter.getId())) {
            throw new BusinessException("Você não pode denunciar o seu próprio anúncio.");
        }

        report.setTargetName(animal.getName());
        report.setTargetPhotoUrl(animalRepository.findCoverPhotos(List.of(animal.getId())).stream()
                .findFirst().map(AnimalRepository.CoverPhoto::getUrl).orElse(null));
        report.setTargetSpecies(animal.getSpecies());
        report.setTargetOwnerId(owner.getId());
        report.setTargetOwnerName(owner.getName());
        report.setTargetOwnerUserType(owner.getUserType());
    }

    /** O perfil precisa ser público (conta ativa de pessoa ou ONG). */
    private void copyUser(Report report, User reporter) {
        User target = userRepository.findById(report.getTargetId())
                .filter(found -> Boolean.TRUE.equals(found.getActive()))
                .filter(found -> found.getUserType() != UserType.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil não encontrado."));
        if (target.getId().equals(reporter.getId())) {
            throw new BusinessException("Você não pode denunciar o seu próprio perfil.");
        }

        report.setTargetName(target.getName());
        report.setTargetPhotoUrl(target.getPhotoUrl());
        report.setTargetUserType(target.getUserType());
        report.setTargetCity(target.getCity());
        report.setTargetState(target.getState());
    }

    private static ConflictException duplicate(ReportTargetType targetType) {
        return new ConflictException(targetType == ReportTargetType.ANIMAL
                ? "Você já denunciou este anúncio. A denúncia está em análise."
                : "Você já denunciou este perfil. A denúncia está em análise.");
    }
}
