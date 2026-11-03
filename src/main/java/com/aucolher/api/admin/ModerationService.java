package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminUserDTO;
import com.aucolher.api.admin.dto.AdminUserProfileDTO;
import com.aucolher.api.admin.dto.AdminUserUpdateDTO;
import com.aucolher.api.adoption.AdoptionRequestService;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalDetailDTO;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.campaign.dto.CampaignRequestDTO;
import com.aucolher.api.campaign.dto.CampaignResponseDTO;
import com.aucolher.api.event.EventService;
import com.aucolher.api.event.dto.EventRequestDTO;
import com.aucolher.api.event.dto.EventResponseDTO;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.report.dto.ReportDTO;
import com.aucolher.api.report.entity.Report;
import com.aucolher.api.report.entity.ReportResolution;
import com.aucolher.api.report.entity.ReportTargetType;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.UserService;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * As decisões do admin que mexem em vários módulos — banir, reativar, remover
 * anúncio, evento ou campanha, arquivar denúncia —, cada uma numa transação só.
 *
 * Banir não apaga nem altera o conteúdo da conta: os animais, eventos e
 * campanhas dela somem das rotas públicas pelo filtro de conta ativa, e voltam
 * se ela for reativada. Pedidos cancelados e denúncias resolvidas não voltam.
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final UserRepository userRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final EventService eventService;
    private final CampaignService campaignService;
    private final ReportService reportService;
    private final AdoptionRequestService requestService;
    private final UserService userService;

    // ===================== Edição (o conteúdo segue em nome do dono) =====================

    /** A conta inteira de uma pessoa ou ONG, com o e-mail de login, para o formulário de edição. */
    @Transactional(readOnly = true)
    public AdminUserProfileDTO getUser(Long userId) {
        return AdminUserProfileDTO.from(userService.findForModeration(userId));
    }

    @Transactional
    public AdminUserProfileDTO updateUser(Long userId, AdminUserUpdateDTO body, String adminEmail) {
        return AdminUserProfileDTO.from(
                userService.updateByModeration(userId, body.email(), body.profile(), findAdmin(adminEmail).getId()));
    }

    @Transactional
    public AnimalDetailDTO updateAnimal(Long animalId, AnimalRequestDTO body, String adminEmail) {
        return animalService.updateByModeration(animalId, body, findAdmin(adminEmail).getId());
    }

    @Transactional
    public EventResponseDTO updateEvent(Long eventId, EventRequestDTO body, String adminEmail) {
        return eventService.updateByModeration(eventId, body, findAdmin(adminEmail).getId());
    }

    @Transactional
    public CampaignResponseDTO updateCampaign(Long campaignId, CampaignRequestDTO body, String adminEmail) {
        return campaignService.updateByModeration(campaignId, body, findAdmin(adminEmail).getId());
    }

    /**
     * Banir (`active: false`) ou reativar (`active: true`) uma conta direto da
     * lista do admin. A conta excluída pela própria pessoa não volta.
     */
    @Transactional
    public AdminUserDTO setUserActive(Long userId, Boolean active, String adminEmail) {
        if (active == null) {
            throw new BusinessException("Informe se a conta fica ativa.");
        }
        User account = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));

        if (active && account.isDeleted()) {
            throw new BusinessException("Contas excluídas não podem ser reativadas.");
        }
        if (active) {
            account.setActive(true);
        } else {
            ban(account, findAdmin(adminEmail));
        }
        return AdminUserDTO.from(account, animalRepository.countByOwnerId(account.getId()));
    }

    /** Bane a conta por trás do caso: a do perfil denunciado ou a dona do anúncio denunciado. */
    @Transactional
    public List<ReportDTO> banByReport(Long reportId, String adminEmail) {
        Report report = findPendingReport(reportId);
        Long accountId = report.getTargetType() == ReportTargetType.USER ? report.getTargetId() : report.getTargetOwnerId();
        User account = accountId == null ? null : userRepository.findById(accountId).orElse(null);
        if (account == null) {
            throw new BusinessException("Não foi possível identificar a conta denunciada.");
        }
        return ban(account, findAdmin(adminEmail)).stream().map(ReportDTO::forAdmin).toList();
    }

    /** Arquiva o caso como improcedente: esta denúncia e as outras pendentes do mesmo alvo. */
    @Transactional
    public List<ReportDTO> dismiss(Long reportId, String adminEmail) {
        Report report = findPendingReport(reportId);
        return reportService.resolveCase(report.getTargetType(), report.getTargetId(), ReportResolution.DISMISSED,
                        findAdmin(adminEmail), LocalDateTime.now())
                .stream().map(ReportDTO::forAdmin).toList();
    }

    /** Remove o anúncio (REMOVED), cancela os pedidos ativos e resolve as denúncias contra ele. */
    @Transactional
    public void removeAnimal(Long animalId, String adminEmail) {
        animalService.removeByModeration(animalId);
        reportService.resolveCase(ReportTargetType.ANIMAL, animalId, ReportResolution.LISTING_REMOVED,
                findAdmin(adminEmail), LocalDateTime.now());
    }

    /** Remove o evento: ele vira CANCELLED, marcado como removido pela moderação. */
    @Transactional
    public void removeEvent(Long eventId) {
        eventService.removeByModeration(eventId);
    }

    /** Remove a campanha: exclusão lógica marcada como removida pela moderação; os PIX pendentes caem. */
    @Transactional
    public void removeCampaign(Long campaignId) {
        campaignService.removeByModeration(campaignId);
    }

    /**
     * O banimento: a conta para de entrar (e o token dela deixa de valer), as
     * denúncias pendentes contra ela são resolvidas e os pedidos ativos em que
     * ela é parte são cancelados (ACCOUNT_BANNED). Devolve as denúncias resolvidas.
     */
    private List<Report> ban(User account, User admin) {
        if (account.getUserType() == UserType.ADMIN) {
            throw new BusinessException("Contas de admin não podem ser banidas.");
        }
        account.setActive(false);
        List<Report> resolved = reportService.resolveAgainstAccount(account.getId(), ReportResolution.ACCOUNT_BANNED,
                admin, LocalDateTime.now());
        requestService.cancelAllForAccount(account.getId(), CancelReason.ACCOUNT_BANNED);
        return resolved;
    }

    private Report findPendingReport(Long reportId) {
        Report report = reportService.findReport(reportId);
        if (!report.isPending()) {
            throw new BusinessException("Esta denúncia já foi julgada.");
        }
        return report;
    }

    private User findAdmin(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
    }
}
