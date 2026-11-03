package com.aucolher.api.campaign;

import com.aucolher.api.campaign.dto.CampaignNgoDTO;
import com.aucolher.api.campaign.dto.CampaignRequestDTO;
import com.aucolher.api.campaign.dto.CampaignResponseDTO;
import com.aucolher.api.campaign.entity.Campaign;
import com.aucolher.api.campaign.entity.CampaignCategory;
import com.aucolher.api.campaign.entity.CampaignStatus;
import com.aucolher.api.donation.DonationRepository;
import com.aucolher.api.donation.entity.DonationStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ConflictException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final Pattern COVER_PATTERN = Pattern.compile(
            "^(https?://\\S+|data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+)$");

    private final CampaignRepository campaigns;
    private final DonationRepository donations;
    private final UserRepository users;
    private final MercadoPagoAccountGateway mercadoPago;

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<CampaignResponseDTO> listPublic(Long ngoId, String status) {
        LocalDate today = LocalDate.now();
        boolean closed = parseClosed(status);
        List<Campaign> list;
        if (closed) {
            list = ngoId == null ? campaigns.findPublicClosed(today)
                    : campaigns.findPublicClosedByNgo(ngoId, today);
        } else {
            list = ngoId == null ? campaigns.findPublicActive(today)
                    : campaigns.findPublicActiveByNgo(ngoId, today);
        }
        return toDtos(list);
    }

    @Transactional(readOnly = true)
    public CampaignResponseDTO getById(Long id) {
        return toDto(findPublic(id));
    }

    @Transactional(readOnly = true)
    public List<CampaignResponseDTO> listMine(Authentication auth) {
        User user = currentUser(auth);
        requireNgo(user, "Apenas contas de ONG possuem campanhas próprias.");
        return toDtos(campaigns.findByNgoIdAndDeletedFalseOrderByCreatedAtDesc(user.getId()));
    }

    /** Painel do admin: todas as campanhas, inclusive as excluídas, com a situação da ONG. */
    @Transactional(readOnly = true)
    public List<CampaignResponseDTO> listForAdmin() {
        return toDtos(campaigns.findAllByOrderByCreatedAtDesc());
    }

    // --------------------------------------------------------------- escrita

    @Transactional
    public CampaignResponseDTO create(CampaignRequestDTO req, Authentication auth) {
        User user = currentUser(auth);
        requireNgo(user, "Apenas contas de ONG podem criar campanhas.");
        if (!mercadoPago.isConnected(user.getId())) {
            throw new BusinessException(
                    "Conecte sua conta do Mercado Pago no painel antes de criar uma campanha.");
        }
        validate(req);
        boolean urgent = Boolean.TRUE.equals(req.isUrgent());
        if (urgent) {
            checkNoOtherUrgent(user.getId(), 0L);
        }
        Campaign c = new Campaign();
        apply(c, req);
        c.setNgoId(user.getId());
        c.setStatus(CampaignStatus.ACTIVE);
        return toDto(campaigns.save(c));
    }

    @Transactional
    public CampaignResponseDTO update(Long id, CampaignRequestDTO req, Authentication auth) {
        User user = currentUser(auth);
        Campaign c = lockExisting(id);                // 404
        requireOwner(c, user);                        // 403
        return save(c, req);
    }

    /**
     * Moderação: o admin edita a campanha de uma ONG, com as mesmas regras
     * dela (encerrada não edita, uma urgente ativa por ONG). A campanha
     * continua em nome da ONG, e fica registrado que a moderação a editou.
     */
    @Transactional
    public CampaignResponseDTO updateByModeration(Long id, CampaignRequestDTO req, Long adminId) {
        Campaign c = lockExisting(id);                // 404 (também se excluída)
        c.markModeratedBy(adminId);
        return save(c, req);
    }

    /** Validação e gravação comuns à ONG dona e ao admin. */
    private CampaignResponseDTO save(Campaign c, CampaignRequestDTO req) {
        if (isClosed(c)) {                            // 400
            throw new BusinessException("Campanhas encerradas não podem ser editadas.");
        }
        validate(req);
        boolean wantsUrgent = Boolean.TRUE.equals(req.isUrgent());
        if (wantsUrgent && !c.isUrgent()) {           // só confere ao LIGAR a urgência
            checkNoOtherUrgent(c.getNgoId(), c.getId());
        }
        apply(c, req);
        return toDto(campaigns.save(c));
    }

    @Transactional
    public CampaignResponseDTO close(Long id, Authentication auth) {
        User user = currentUser(auth);
        Campaign c = lockExisting(id);
        requireOwner(c, user);
        if (isClosed(c)) {
            throw new BusinessException("Esta campanha já está encerrada.");
        }
        donations.cancelPendingByCampaign(c.getId());
        c.setStatus(CampaignStatus.CLOSED);
        c.setClosedAt(LocalDateTime.now());
        return toDto(campaigns.save(c));
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        User user = currentUser(auth);
        Campaign c = lockExisting(id);
        requireOwner(c, user);
        donations.cancelPendingByCampaign(c.getId());
        if (donations.countByCampaignIdAndStatus(c.getId(), DonationStatus.APPROVED) == 0) {
            campaigns.delete(c);                      // exclusão real
        } else {
            c.setDeleted(true);                       // exclusão lógica: histórico continua
            campaigns.save(c);
        }
    }

    /**
     * Moderação: o admin remove a campanha. Ela vira exclusão lógica com
     * removed_by_moderation, mesmo sem doações (a linha fica, para o admin
     * ver o que removeu), e os PIX ainda não pagos são cancelados. As doações
     * aprovadas continuam no total e no histórico. Já excluída ou inexistente: 404.
     */
    @Transactional
    public void removeByModeration(Long id) {
        Campaign c = lockExisting(id);
        donations.cancelPendingByCampaign(c.getId());
        c.setDeleted(true);
        c.setRemovedByModeration(true);
        campaigns.save(c);
    }

    /**
     * Exclusão da conta da ONG: todas as campanhas dela viram exclusão lógica,
     * mesmo sem doações, e as ativas são encerradas antes. Os PIX ainda não
     * pagos caem, como no "Encerrar". As doações aprovadas continuam no
     * histórico de quem doou, com a campanha "removida".
     */
    @Transactional
    public void closeAndHideAllOf(Long ngoId) {
        LocalDateTime now = LocalDateTime.now();
        for (Campaign c : campaigns.findByNgoIdAndDeletedFalseOrderByCreatedAtDesc(ngoId)) {
            donations.cancelPendingByCampaign(c.getId());   // limpa o contexto: o save abaixo faz o merge
            if (!isClosed(c)) {
                c.setStatus(CampaignStatus.CLOSED);
                c.setClosedAt(now);
            }
            c.setDeleted(true);
            campaigns.save(c);
        }
    }

    // ---------------------------------------------------------------- regras

    private boolean parseClosed(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ACTIVE")) {
            return false;
        }
        if (status.equalsIgnoreCase("CLOSED")) {
            return true;
        }
        throw new BusinessException("Status inválido. Use ACTIVE ou CLOSED.");
    }

    /** Encerrada = status CLOSED, ou prazo vencido (calculado na leitura). */
    public boolean isClosed(Campaign c) {
        return c.isClosed();
    }

    private void checkNoOtherUrgent(Long ngoId, Long excludeId) {
        List<Campaign> others = campaigns.findOtherActiveUrgent(ngoId, excludeId, LocalDate.now());
        if (!others.isEmpty()) {
            throw new ConflictException("A ONG já tem uma campanha urgente ativa (\""
                    + others.get(0).getTitle() + "\"). Tire a urgência dela antes de marcar outra.");
        }
    }

    private void validate(CampaignRequestDTO req) {
        if (req.category() == CampaignCategory.OTHER
                && (req.customCategory() == null || req.customCategory().isBlank())) {
            throw new BusinessException("Dê um nome para a categoria.");
        }
        if (req.deadline() != null && req.deadline().isBefore(LocalDate.now())) {
            throw new BusinessException("O prazo não pode ser anterior a hoje.");
        }
        String cover = req.coverUrl();
        if (cover != null) {
            if (cover.length() > PhotoUrl.COVER_MAX_LENGTH) {
                throw new BusinessException("A imagem de capa é grande demais.");
            }
            if (!COVER_PATTERN.matcher(cover).matches()) {
                throw new BusinessException(
                        "A capa deve ser um link http(s) ou uma imagem JPEG, PNG ou WebP.");
            }
        }
    }

    private void apply(Campaign c, CampaignRequestDTO r) {
        c.setTitle(r.title().trim());
        c.setDescription(r.description());
        c.setCategory(r.category());
        c.setCustomCategory(r.category() == CampaignCategory.OTHER ? r.customCategory().trim() : null);
        c.setUrgent(Boolean.TRUE.equals(r.isUrgent()));
        c.setGoalAmount(BigDecimal.valueOf(r.goalAmount()));
        c.setDeadline(r.deadline());
        c.setCoverUrl(r.coverUrl());
    }

    // --------------------------------------------------------------- acessos

    /** Rotas públicas: 404 se não existe, foi excluída ou a ONG está banida. */
    public Campaign findPublic(Long id) {
        return campaigns.findById(id)
                .filter(c -> !c.isDeleted())
                .filter(c -> users.findById(c.getNgoId()).map(this::isActive).orElse(false))
                .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada."));
    }

    private Campaign lockExisting(Long id) {
        return campaigns.findByIdForUpdate(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada."));
    }

    private void requireNgo(User user, String message) {
        if (!isNgo(user)) {
            throw new ForbiddenException(message);
        }
    }

    private void requireOwner(Campaign c, User user) {
        if (!isNgo(user) || !c.getNgoId().equals(user.getId())) {
            throw new ForbiddenException("Apenas a ONG dona da campanha pode fazer isso.");
        }
    }

    /** Usuário logado. Assume que o subject do JWT / Authentication#getName() é o e-mail. */
    private User currentUser(Authentication auth) {
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ForbiddenException("Usuário não encontrado."));
    }

    private boolean isNgo(User user) {
        return user.getUserType() == UserType.NGO;
    }

    private boolean isActive(User user) {
        return Boolean.TRUE.equals(user.getActive());
    }

    private CampaignNgoDTO toNgo(User ngo) {
        if (ngo == null) {
            return null;
        }
        return new CampaignNgoDTO(ngo.getId(), ngo.getName(), Boolean.TRUE.equals(ngo.getVerified()),
                ngo.getPhotoUrl(), ngo.getCity(), ngo.getState(), isActive(ngo));
    }

    private CampaignResponseDTO toDto(Campaign c) {
        return toDtos(List.of(c)).get(0);
    }

    /** Monta os DTOs com 2 consultas extras (totais e ONGs), sem N+1. */
    private List<CampaignResponseDTO> toDtos(List<Campaign> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Campaign::getId).toList();
        Map<Long, BigDecimal> raised = new HashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : donations.totalsByCampaignIds(ids)) {
            raised.put((Long) row[0], (BigDecimal) row[1]);
            counts.put((Long) row[0], (Long) row[2]);
        }
        Set<Long> ngoIds = list.stream().map(Campaign::getNgoId).collect(Collectors.toSet());
        Map<Long, User> ngos = users.findAllById(ngoIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Boolean> connected = new HashMap<>();
        for (Long ngoId : ngoIds) {
            connected.put(ngoId, mercadoPago.isConnected(ngoId));
        }
        return list.stream().map(c -> {
            boolean closed = isClosed(c);
            LocalDateTime closedAt = c.getClosedAt();
            if (closedAt == null && c.deadlinePassed()) {
                closedAt = c.getDeadline().atTime(23, 59, 59);
            }
            return new CampaignResponseDTO(
                    c.getId(), c.getTitle(), c.getDescription(), c.getCategory(), c.getCustomCategory(),
                    c.isUrgent(), c.getGoalAmount(),
                    raised.getOrDefault(c.getId(), BigDecimal.ZERO),
                    counts.getOrDefault(c.getId(), 0L),
                    c.getCoverUrl(), c.getDeadline(),
                    closed ? CampaignStatus.CLOSED : CampaignStatus.ACTIVE,
                    !closed && !c.isDeleted() && connected.getOrDefault(c.getNgoId(), false),
                    c.getCreatedAt(), closedAt,
                    c.isDeleted(), c.isRemovedByModeration(), c.getModeratedAt(),
                    toNgo(ngos.get(c.getNgoId())));
        }).toList();
    }
}
