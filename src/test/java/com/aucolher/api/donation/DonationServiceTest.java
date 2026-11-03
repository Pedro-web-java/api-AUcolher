package com.aucolher.api.donation;

import com.aucolher.api.campaign.CampaignRepository;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.campaign.MercadoPagoAccountGateway;
import com.aucolher.api.campaign.entity.Campaign;
import com.aucolher.api.campaign.entity.CampaignStatus;
import com.aucolher.api.donation.dto.DonationRequestDTO;
import com.aucolher.api.donation.dto.DonationResponseDTO;
import com.aucolher.api.donation.entity.Donation;
import com.aucolher.api.donation.entity.DonationStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regras das doações (contrato api-campanhas-doacoes §4 a §6). O Mercado Pago
 * fica simulado: aqui só se confere o que a API faz com a resposta dele.
 */
@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    static final String DONOR_EMAIL = "person@email.com";

    @Mock
    private DonationRepository donations;
    @Mock
    private CampaignRepository campaigns;
    @Mock
    private CampaignService campaignService;
    @Mock
    private UserRepository users;
    @Mock
    private MercadoPagoAccountGateway accounts;
    @Mock
    private MercadoPagoPixClient pix;

    @InjectMocks
    private DonationService service;

    // ===================== Criar =====================

    @Test
    void donationCreatesAPendingPixWithTheNgoToken() {
        givenLogged(person());
        Campaign c = campaign();
        when(campaignService.findPublic(4L)).thenReturn(c);
        when(accounts.isConnected(1L)).thenReturn(true);
        when(accounts.accessToken(1L)).thenReturn("token-da-ong");
        givenSavedWithId(259L);
        when(pix.createPixOrder(eq("token-da-ong"), eq(259L), any(), eq(DONOR_EMAIL), anyString()))
                .thenReturn(new MercadoPagoPixClient.PixOrder("ORD123", "00020126pix", "iVBOR", "https://mp/ticket"));
        when(users.findById(1L)).thenReturn(Optional.of(ngo()));

        DonationResponseDTO dto = service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth());

        assertThat(dto.status()).isEqualTo(DonationStatus.PENDING);
        assertThat(dto.amount()).isEqualByComparingTo("50");
        assertThat(dto.pix().qrCode()).isEqualTo("00020126pix");
        assertThat(dto.pix().expiresAt()).isEqualTo(dto.createdAt().plusMinutes(30));
        assertThat(dto.campaign().ngoName()).isEqualTo("Patinhas");
        assertThat(dto.campaign().removed()).isFalse();
        assertThat(dto.donor()).isNull();
    }

    @Test
    void adminDoesNotDonate() {
        givenLogged(User.builder().id(9L).email(DONOR_EMAIL).userType(UserType.ADMIN).build());
        when(campaignService.findPublic(4L)).thenReturn(campaign());

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Contas de admin não fazem doações.");
    }

    @Test
    void missingCampaignIsNotFoundBeforeTheAdminCheck() {
        givenLogged(User.builder().id(9L).email(DONOR_EMAIL).userType(UserType.ADMIN).build());
        when(campaignService.findPublic(4L)).thenThrow(new ResourceNotFoundException("Campanha não encontrada."));

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ngoDoesNotDonateToItsOwnCampaign() {
        givenLogged(ngo());
        when(campaignService.findPublic(4L)).thenReturn(campaign());

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Você não pode doar para a sua própria campanha.");
    }

    @Test
    void closedCampaignDoesNotReceive() {
        givenLogged(person());
        Campaign c = campaign();
        when(campaignService.findPublic(4L)).thenReturn(c);
        when(campaignService.isClosed(c)).thenReturn(true);

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta campanha está encerrada e não recebe mais doações.");
    }

    @Test
    void amountRules() {
        givenLogged(person());
        when(campaignService.findPublic(4L)).thenReturn(campaign());

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("4")), auth()))
                .hasMessage("A doação mínima é de R$ 5.");
        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("10.50")), auth()))
                .hasMessage("Informe um valor em reais, sem centavos.");
        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("100001")), auth()))
                .isInstanceOf(BusinessException.class);
        verify(donations, never()).saveAndFlush(any());
    }

    @Test
    void ngoWithoutMercadoPagoDoesNotReceive() {
        givenLogged(person());
        when(campaignService.findPublic(4L)).thenReturn(campaign());
        when(accounts.isConnected(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta ONG não está recebendo doações no momento.");
    }

    @Test
    void mercadoPagoFailureBecomesA400() {
        givenLogged(person());
        when(campaignService.findPublic(4L)).thenReturn(campaign());
        when(accounts.isConnected(1L)).thenReturn(true);
        when(accounts.accessToken(1L)).thenReturn("token-da-ong");
        givenSavedWithId(259L);
        when(pix.createPixOrder(any(), anyLong(), any(), any(), any())).thenThrow(new MercadoPagoException("recusou"));

        assertThatThrownBy(() -> service.create(4L, new DonationRequestDTO(new BigDecimal("50")), auth()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não foi possível gerar o PIX agora. Tente novamente em instantes.");
    }

    // ===================== Consulta (polling do modal) =====================

    @Test
    void pollingApprovesWhenTheOrderIsPaid() {
        givenLogged(person());
        Donation pending = donation(DonationStatus.PENDING, LocalDateTime.now().plusMinutes(20));
        Donation approved = donation(DonationStatus.APPROVED, pending.getExpiresAt());
        approved.setApprovedAt(LocalDateTime.now());
        when(donations.findById(259L)).thenReturn(Optional.of(pending), Optional.of(approved));
        when(accounts.accessToken(1L)).thenReturn("token-da-ong");
        when(pix.getOrder("token-da-ong", "ORD123")).thenReturn(new MercadoPagoPixClient.OrderState(true, "processed", "accredited"));

        DonationResponseDTO dto = service.getById(259L, auth());

        verify(donations).approve(eq(259L), any());
        assertThat(dto.status()).isEqualTo(DonationStatus.APPROVED);
        assertThat(dto.pix()).isNull();
    }

    @Test
    void pendingKeepsThePixForTheDonor() {
        givenLogged(person());
        when(donations.findById(259L)).thenReturn(Optional.of(donation(DonationStatus.PENDING, LocalDateTime.now().plusMinutes(20))));
        when(accounts.accessToken(1L)).thenReturn("token-da-ong");
        when(pix.getOrder("token-da-ong", "ORD123")).thenReturn(new MercadoPagoPixClient.OrderState(false, "action_required", "waiting_transfer"));

        DonationResponseDTO dto = service.getById(259L, auth());

        verify(donations, never()).approve(anyLong(), any());
        assertThat(dto.status()).isEqualTo(DonationStatus.PENDING);
        assertThat(dto.pix().qrCode()).isEqualTo("00020126pix");
    }

    @Test
    void pixExpiresAfterThirtyMinutes() {
        givenLogged(person());
        when(donations.findById(259L)).thenReturn(Optional.of(donation(DonationStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        DonationResponseDTO dto = service.getById(259L, auth());

        verifyNoInteractions(pix);
        assertThat(dto.status()).isEqualTo(DonationStatus.EXPIRED);
        assertThat(dto.pix()).isNull();
    }

    @Test
    void someoneElsesDonationIsForbidden() {
        givenLogged(User.builder().id(7L).email(DONOR_EMAIL).userType(UserType.PERSON).build());
        when(donations.findById(259L)).thenReturn(Optional.of(donation(DonationStatus.APPROVED, LocalDateTime.now())));

        assertThatThrownBy(() -> service.getById(259L, auth())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedCampaignKeepsTheSavedTitle() {
        givenLogged(person());
        Donation d = donation(DonationStatus.APPROVED, LocalDateTime.now());
        d.setCampaignId(null);
        when(donations.findById(259L)).thenReturn(Optional.of(d));

        DonationResponseDTO dto = service.getById(259L, auth());

        assertThat(dto.campaign().title()).isEqualTo("Ração (título na doação)");
        assertThat(dto.campaign().removed()).isTrue();
        assertThat(dto.campaign().removedByModeration()).isFalse();
    }

    @Test
    void campaignRemovedByModerationTellsTheDonor() {
        givenLogged(person());
        when(donations.findById(259L)).thenReturn(Optional.of(donation(DonationStatus.APPROVED, LocalDateTime.now())));
        Campaign c = campaign();
        c.setDeleted(true);
        c.setRemovedByModeration(true);
        when(campaigns.findById(4L)).thenReturn(Optional.of(c));

        DonationResponseDTO dto = service.getById(259L, auth());

        assertThat(dto.campaign().title()).isEqualTo("Ração");
        assertThat(dto.campaign().removed()).isTrue();
        assertThat(dto.campaign().removedByModeration()).isTrue();
    }

    // ===================== Listas =====================

    @Test
    void onlyNgosListReceivedDonations() {
        givenLogged(person());

        assertThatThrownBy(() -> service.listReceived(auth())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void receivedDonationsShowTheDonor() {
        givenLogged(ngo());
        Donation d = donation(DonationStatus.APPROVED, LocalDateTime.now());
        when(donations.findByNgoIdAndStatusOrderByApprovedAtDesc(1L, DonationStatus.APPROVED)).thenReturn(List.of(d));
        when(campaigns.findAllById(any())).thenReturn(List.of(campaign()));
        when(users.findAllById(any())).thenReturn(List.of(ngo(), person()));

        DonationResponseDTO dto = service.listReceived(auth()).get(0);

        assertThat(dto.donor().name()).isEqualTo("Maria");
        assertThat(dto.pix()).isNull();
    }

    // ===================== Webhook =====================

    @Test
    void webhookIgnoresUnknownOrders() {
        when(donations.findByMpOrderId("OUTRA")).thenReturn(Optional.empty());

        service.onWebhook("OUTRA");

        verifyNoInteractions(pix);
    }

    // ===================== Apoio =====================

    private void givenLogged(User user) {
        when(users.findByEmail(DONOR_EMAIL)).thenReturn(Optional.of(user));
    }

    private void givenSavedWithId(Long id) {
        when(donations.saveAndFlush(any(Donation.class))).thenAnswer(inv -> {
            Donation d = inv.getArgument(0);
            d.setId(id);
            return d;
        });
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken(DONOR_EMAIL, null, List.of());
    }

    private static User person() {
        return User.builder().id(5L).name("Maria").email(DONOR_EMAIL).userType(UserType.PERSON).active(true).build();
    }

    private static User ngo() {
        return User.builder().id(1L).name("Patinhas").email(DONOR_EMAIL).userType(UserType.NGO).active(true).build();
    }

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.setId(4L);
        c.setNgoId(1L);
        c.setTitle("Ração");
        c.setStatus(CampaignStatus.ACTIVE);
        return c;
    }

    private static Donation donation(DonationStatus status, LocalDateTime expiresAt) {
        Donation d = new Donation();
        d.setId(259L);
        d.setCampaignId(4L);
        d.setCampaignTitle("Ração (título na doação)");
        d.setNgoId(1L);
        d.setDonorId(5L);
        d.setAmount(new BigDecimal("50.00"));
        d.setStatus(status);
        d.setMpOrderId("ORD123");
        d.setPixQrCode("00020126pix");
        d.setCreatedAt(expiresAt.minusMinutes(30));
        d.setExpiresAt(expiresAt);
        return d;
    }
}
