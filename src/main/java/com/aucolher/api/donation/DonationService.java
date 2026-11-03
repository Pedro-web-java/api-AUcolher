package com.aucolher.api.donation;

import com.aucolher.api.campaign.CampaignRepository;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.campaign.MercadoPagoAccountGateway;
import com.aucolher.api.campaign.entity.Campaign;
import com.aucolher.api.donation.dto.*;
import com.aucolher.api.donation.entity.Donation;
import com.aucolher.api.donation.entity.DonationStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private static final BigDecimal MIN_AMOUNT = BigDecimal.valueOf(5);
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(100_000);
    private static final int PIX_MINUTES = 30;

    private final DonationRepository donations;
    private final CampaignRepository campaigns;
    private final CampaignService campaignService;
    private final UserRepository users;
    private final MercadoPagoAccountGateway accounts;
    private final MercadoPagoPixClient pix;

    // ----------------------------------------------------------------- criar

    @Transactional
    public DonationResponseDTO create(Long campaignId, DonationRequestDTO req, Authentication auth) {
        User user = currentUser(auth);
        Campaign campaign = campaignService.findPublic(campaignId);         // 404
        if (user.getUserType() == UserType.ADMIN) {                         // 403
            throw new ForbiddenException("Contas de admin não fazem doações.");
        }
        // 400, na ordem do contrato
        if (campaign.getNgoId().equals(user.getId())) {
            throw new BusinessException("Você não pode doar para a sua própria campanha.");
        }
        if (campaignService.isClosed(campaign)) {
            throw new BusinessException("Esta campanha está encerrada e não recebe mais doações.");
        }
        BigDecimal amount = validateAmount(req);
        if (!accounts.isConnected(campaign.getNgoId())) {
            throw new BusinessException("Esta ONG não está recebendo doações no momento.");
        }

        Donation d = new Donation();
        d.setCampaignId(campaign.getId());
        d.setCampaignTitle(campaign.getTitle());
        d.setNgoId(campaign.getNgoId());
        d.setDonorId(user.getId());
        d.setAmount(amount);
        d.setStatus(DonationStatus.PENDING);
        d.setCreatedAt(LocalDateTime.now());
        d.setExpiresAt(d.getCreatedAt().plusMinutes(PIX_MINUTES));
        donations.saveAndFlush(d);                                          // precisa do id (idempotência)

        MercadoPagoPixClient.PixOrder order;
        try {
            order = pix.createPixOrder(accounts.accessToken(campaign.getNgoId()), d.getId(),
                    amount, user.getEmail(), user.getName());
        } catch (MercadoPagoException e) {
            // a transação desfaz a doação; o doador pode tentar de novo
            throw new BusinessException("Não foi possível gerar o PIX agora. Tente novamente em instantes.");
        }
        d.setMpOrderId(order.orderId());
        d.setPixQrCode(order.qrCode());
        donations.save(d);

        DonationPixDTO pixDto = new DonationPixDTO(order.qrCode(), order.qrCodeBase64(),
                order.ticketUrl(), d.getExpiresAt());
        return build(d, campaign, userName(campaign.getNgoId()), null, pixDto);
    }

    private BigDecimal validateAmount(DonationRequestDTO req) {
        BigDecimal amount = req == null ? null : req.amount();
        if (amount == null) {
            throw new BusinessException("Informe o valor da doação.");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("Informe um valor em reais, sem centavos.");
        }
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new BusinessException("A doação mínima é de R$ 5.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException("A doação máxima é de R$ 100.000.");
        }
        return amount.setScale(2);
    }

    // ----------------------------------------------------------------- leitura

    /** Polling do modal: se ainda PENDING, consulta o Mercado Pago antes de responder. */
    @Transactional
    public DonationResponseDTO getById(Long id, Authentication auth) {
        User user = currentUser(auth);
        Donation d = donations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doação não encontrada."));
        if (!d.getDonorId().equals(user.getId())) {
            throw new ForbiddenException("Esta doação não é sua.");
        }
        if (effectiveStatus(d) == DonationStatus.PENDING && d.getMpOrderId() != null) {
            try {
                approveIfPaid(d);
            } catch (MercadoPagoException e) {
                log.warn("Não consegui consultar a order da doação {}: {}", id, e.getMessage());
            }
            d = donations.findById(id).orElseThrow();
        }
        DonationPixDTO pixDto = effectiveStatus(d) == DonationStatus.PENDING
                ? new DonationPixDTO(d.getPixQrCode(), null, null, d.getExpiresAt())
                : null;
        Campaign c = d.getCampaignId() == null ? null : campaigns.findById(d.getCampaignId()).orElse(null);
        return build(d, c, userName(d.getNgoId()), null, pixDto);
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> listMine(Authentication auth) {
        User user = currentUser(auth);
        return toDtos(donations.findByDonorIdOrderByCreatedAtDesc(user.getId()), false);
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> listReceived(Authentication auth) {
        User user = currentUser(auth);
        if (user.getUserType() != UserType.NGO) {
            throw new ForbiddenException("Apenas contas de ONG recebem doações.");
        }
        return toDtos(donations.findByNgoIdAndStatusOrderByApprovedAtDesc(
                user.getId(), DonationStatus.APPROVED), true);
    }

    // ------------------------------------------------------------- aprovação

    /** Webhook: acha a doação pela order e confirma no Mercado Pago (não confia no corpo recebido). */
    @Transactional
    public void onWebhook(String orderId) {
        donations.findByMpOrderId(orderId).ifPresent(this::approveIfPaid);
    }

    /** Consulta a order com o token da ONG (donations.ngo_id) e aprova de forma atômica. */
    private void approveIfPaid(Donation d) {
        MercadoPagoPixClient.OrderState state =
                pix.getOrder(accounts.accessToken(d.getNgoId()), d.getMpOrderId());
        if (state.paid()) {
            donations.approve(d.getId(), LocalDateTime.now());   // 0 linhas = outro caminho já aprovou
        }
    }

    // ---------------------------------------------------------------- montagem

    private DonationStatus effectiveStatus(Donation d) {
        if (d.getStatus() == DonationStatus.PENDING && d.getExpiresAt().isBefore(LocalDateTime.now())) {
            return DonationStatus.EXPIRED;
        }
        return d.getStatus();
    }

    private List<DonationResponseDTO> toDtos(List<Donation> list, boolean withDonor) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> campaignIds = list.stream().map(Donation::getCampaignId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Campaign> byCampaign = campaigns.findAllById(campaignIds).stream()
                .collect(Collectors.toMap(Campaign::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        list.forEach(d -> {
            userIds.add(d.getNgoId());
            if (withDonor) userIds.add(d.getDonorId());
        });
        Map<Long, User> byUser = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(d -> {
            User ngo = byUser.get(d.getNgoId());
            User donor = withDonor ? byUser.get(d.getDonorId()) : null;
            return build(d, byCampaign.get(d.getCampaignId()), ngo != null ? ngo.getName() : null, donor, null);
        }).toList();
    }

    private DonationResponseDTO build(Donation d, Campaign c, String ngoName, User donor, DonationPixDTO pixDto) {
        // Campanha existente: título atual. Apagada de vez: vale o título guardado na doação.
        String title = c != null ? c.getTitle() : d.getCampaignTitle();
        boolean removed = c == null || c.isDeleted();
        // A remoção pela moderação é sempre lógica: a campanha continua na tabela
        boolean removedByModeration = c != null && c.isRemovedByModeration();
        DonationCampaignDTO campaign = new DonationCampaignDTO(
                c != null ? c.getId() : null, title, ngoName, removed, removedByModeration);
        DonationDonorDTO donorDto = donor == null ? null
                : new DonationDonorDTO(donor.getId(), donor.getName(), donor.getPhotoUrl());
        return new DonationResponseDTO(d.getId(), d.getAmount(), effectiveStatus(d),
                d.getCreatedAt(), d.getApprovedAt(), pixDto, campaign, donorDto);
    }

    /** Usuário logado. Assume que o subject do JWT / Authentication#getName() é o e-mail. */
    private User currentUser(Authentication auth) {
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ForbiddenException("Usuário não encontrado."));
    }

    private String userName(Long userId) {
        return users.findById(userId).map(User::getName).orElse(null);
    }
}
