package com.aucolher.api.campaign;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Regras das campanhas (contrato api-campanhas-doacoes §1 e §5), com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    static final String NGO_EMAIL = "ngo@email.com";

    @Mock
    private CampaignRepository campaigns;
    @Mock
    private DonationRepository donations;
    @Mock
    private UserRepository users;
    @Mock
    private MercadoPagoAccountGateway mercadoPago;

    @InjectMocks
    private CampaignService service;

    // ===================== Criar =====================

    @Test
    void ngoCreatesAnActiveCampaign() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(true);
        givenSaved();

        CampaignResponseDTO created = service.create(request(CampaignCategory.HEALTH, null, false), auth(NGO_EMAIL));

        assertThat(created.status()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(created.goalAmount()).isEqualByComparingTo("8000");
        assertThat(created.raisedAmount()).isEqualByComparingTo("0");
        assertThat(created.acceptingDonations()).isTrue();
        assertThat(created.customCategory()).isNull();
    }

    @Test
    void personDoesNotCreateCampaigns() {
        givenLogged(User.builder().id(5L).email(NGO_EMAIL).userType(UserType.PERSON).build());

        assertThatThrownBy(() -> service.create(request(CampaignCategory.HEALTH, null, false), auth(NGO_EMAIL)))
                .isInstanceOf(ForbiddenException.class);
        verify(campaigns, never()).save(any());
    }

    @Test
    void creatingWithoutMercadoPagoIsRefused() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(CampaignCategory.HEALTH, null, false), auth(NGO_EMAIL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Conecte sua conta do Mercado Pago no painel antes de criar uma campanha.");
    }

    @Test
    void otherCategoryNeedsAName() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(CampaignCategory.OTHER, "  ", false), auth(NGO_EMAIL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Dê um nome para a categoria.");
    }

    @Test
    void customCategoryIsDroppedOutsideOther() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(true);
        givenSaved();

        CampaignResponseDTO created = service.create(request(CampaignCategory.FOOD, "Ração", false), auth(NGO_EMAIL));

        assertThat(created.customCategory()).isNull();
    }

    @Test
    void secondUrgentCampaignIsAConflict() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(true);
        when(campaigns.findOtherActiveUrgent(eq(1L), eq(0L), any())).thenReturn(List.of(campaign(3L, 1L, "Cirurgia do Rex")));

        assertThatThrownBy(() -> service.create(request(CampaignCategory.HEALTH, null, true), auth(NGO_EMAIL)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A ONG já tem uma campanha urgente ativa (\"Cirurgia do Rex\"). Tire a urgência dela antes de marcar outra.");
    }

    @Test
    void pastDeadlineIsRefused() {
        givenLogged(ngo(1L));
        when(mercadoPago.isConnected(1L)).thenReturn(true);
        CampaignRequestDTO req = new CampaignRequestDTO("Ração", "Ração para o inverno", CampaignCategory.FOOD, null,
                false, 500L, LocalDate.now().minusDays(1), null);

        assertThatThrownBy(() -> service.create(req, auth(NGO_EMAIL))).isInstanceOf(BusinessException.class);
    }

    // ===================== Editar e encerrar =====================

    @Test
    void keepingTheUrgencyDoesNotCheckOtherCampaigns() {
        givenLogged(ngo(1L));
        Campaign existing = campaign(4L, 1L, "Ração");
        existing.setUrgent(true);
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(existing));
        givenSaved();

        service.update(4L, request(CampaignCategory.HEALTH, null, true), auth(NGO_EMAIL));

        verify(campaigns, never()).findOtherActiveUrgent(anyLong(), anyLong(), any());
    }

    @Test
    void turningOnTheUrgencyChecksOtherCampaigns() {
        givenLogged(ngo(1L));
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(campaign(4L, 1L, "Ração")));
        when(campaigns.findOtherActiveUrgent(eq(1L), eq(4L), any())).thenReturn(List.of(campaign(3L, 1L, "Cirurgia do Rex")));

        assertThatThrownBy(() -> service.update(4L, request(CampaignCategory.HEALTH, null, true), auth(NGO_EMAIL)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyTheOwnerEdits() {
        givenLogged(ngo(2L));
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(campaign(4L, 1L, "Ração")));

        assertThatThrownBy(() -> service.update(4L, request(CampaignCategory.HEALTH, null, false), auth(NGO_EMAIL)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void closedCampaignIsReadOnly() {
        givenLogged(ngo(1L));
        Campaign closed = campaign(4L, 1L, "Ração");
        closed.setStatus(CampaignStatus.CLOSED);
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.update(4L, request(CampaignCategory.HEALTH, null, false), auth(NGO_EMAIL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Campanhas encerradas não podem ser editadas.");
        assertThatThrownBy(() -> service.close(4L, auth(NGO_EMAIL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta campanha já está encerrada.");
    }

    @Test
    void closingCancelsThePendingPix() {
        givenLogged(ngo(1L));
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(campaign(4L, 1L, "Ração")));
        givenSaved();

        CampaignResponseDTO closed = service.close(4L, auth(NGO_EMAIL));

        verify(donations).cancelPendingByCampaign(4L);
        assertThat(closed.status()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.acceptingDonations()).isFalse();
    }

    @Test
    void passedDeadlineReadsAsClosed() {
        Campaign expired = campaign(4L, 1L, "Ração");
        expired.setDeadline(LocalDate.now().minusDays(2));
        when(campaigns.findById(4L)).thenReturn(Optional.of(expired));
        when(users.findById(1L)).thenReturn(Optional.of(ngo(1L)));

        CampaignResponseDTO dto = service.getById(4L);

        assertThat(dto.status()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(dto.closedAt()).isEqualTo(expired.getDeadline().atTime(23, 59, 59));
    }

    // ===================== Excluir e visibilidade =====================

    @Test
    void deletingWithoutApprovedDonationsIsReal() {
        givenLogged(ngo(1L));
        Campaign c = campaign(4L, 1L, "Ração");
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));
        when(donations.countByCampaignIdAndStatus(4L, DonationStatus.APPROVED)).thenReturn(0L);

        service.delete(4L, auth(NGO_EMAIL));

        verify(donations).cancelPendingByCampaign(4L);
        verify(campaigns).delete(c);
    }

    @Test
    void deletingWithApprovedDonationsIsLogical() {
        givenLogged(ngo(1L));
        Campaign c = campaign(4L, 1L, "Ração");
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));
        when(donations.countByCampaignIdAndStatus(4L, DonationStatus.APPROVED)).thenReturn(3L);

        service.delete(4L, auth(NGO_EMAIL));

        assertThat(c.isDeleted()).isTrue();
        verify(campaigns, never()).delete(any());
    }

    @Test
    void deletedOrBannedCampaignIsNotFound() {
        Campaign deleted = campaign(4L, 1L, "Ração");
        deleted.setDeleted(true);
        when(campaigns.findById(4L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.getById(4L)).isInstanceOf(ResourceNotFoundException.class);

        User banned = ngo(2L);
        banned.setActive(false);
        when(campaigns.findById(5L)).thenReturn(Optional.of(campaign(5L, 2L, "Castração")));
        when(users.findById(2L)).thenReturn(Optional.of(banned));
        assertThatThrownBy(() -> service.getById(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void raisedAmountComesFromApprovedDonations() {
        Campaign c = campaign(4L, 1L, "Ração");
        when(campaigns.findPublicActive(any())).thenReturn(List.of(c));
        when(donations.totalsByCampaignIds(List.of(4L))).thenReturn(List.<Object[]>of(new Object[]{4L, new BigDecimal("3250.00"), 25L}));
        when(users.findAllById(any())).thenReturn(List.of(ngo(1L)));
        when(mercadoPago.isConnected(1L)).thenReturn(false);

        CampaignResponseDTO dto = service.listPublic(null, null).get(0);

        assertThat(dto.raisedAmount()).isEqualByComparingTo("3250");
        assertThat(dto.donationsCount()).isEqualTo(25);
        assertThat(dto.ngo().name()).isEqualTo("Patinhas");
        // Conta do Mercado Pago caiu: a campanha continua visível, mas não recebe
        assertThat(dto.acceptingDonations()).isFalse();
    }

    @Test
    void invalidStatusFilterIsRefused() {
        assertThatThrownBy(() -> service.listPublic(null, "PAUSED")).isInstanceOf(BusinessException.class);
    }

    // ===================== Admin e moderação =====================

    @Test
    void adminEditMarksTheModeration() {
        Campaign c = campaign(4L, 1L, "Ração");
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));
        givenSaved();

        CampaignResponseDTO dto = service.updateByModeration(4L, request(CampaignCategory.FOOD, null, false), 99L);

        assertThat(c.getModeratedBy()).isEqualTo(99L);
        assertThat(dto.moderatedAt()).isNotNull();
        assertThat(dto.title()).isEqualTo("Cirurgia do Rex");
    }

    @Test
    void adminDoesNotEditAClosedCampaign() {
        Campaign c = campaign(4L, 1L, "Ração");
        c.setStatus(CampaignStatus.CLOSED);
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.updateByModeration(4L, request(CampaignCategory.FOOD, null, false), 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void moderationRemovalIsAlwaysLogical() {
        Campaign c = campaign(4L, 1L, "Ração");
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));

        service.removeByModeration(4L);

        verify(donations).cancelPendingByCampaign(4L);
        assertThat(c.isDeleted()).isTrue();
        assertThat(c.isRemovedByModeration()).isTrue();
        verify(campaigns).save(c);
        verify(campaigns, never()).delete(any());
    }

    @Test
    void removingAnAlreadyDeletedCampaignIsNotFound() {
        Campaign c = campaign(4L, 1L, "Ração");
        c.setDeleted(true);
        when(campaigns.findByIdForUpdate(4L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.removeByModeration(4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Campanha não encontrada.");
    }

    @Test
    void adminListShowsRemovedCampaignsAndBannedNgos() {
        Campaign removed = campaign(4L, 1L, "Ração");
        removed.setDeleted(true);
        removed.setRemovedByModeration(true);
        User banned = ngo(1L);
        banned.setActive(false);
        when(campaigns.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(removed));
        when(users.findAllById(any())).thenReturn(List.of(banned));
        when(mercadoPago.isConnected(1L)).thenReturn(true);

        CampaignResponseDTO dto = service.listForAdmin().get(0);

        assertThat(dto.removed()).isTrue();
        assertThat(dto.removedByModeration()).isTrue();
        assertThat(dto.ngo().active()).isFalse();
        assertThat(dto.acceptingDonations()).isFalse();
    }

    // ===================== Exclusão da conta da ONG =====================

    @Test
    void deletingTheNgoAccountClosesAndHidesEveryCampaign() {
        Campaign active = campaign(4L, 1L, "Ração");
        Campaign closed = campaign(5L, 1L, "Castração");
        LocalDateTime closedAt = LocalDateTime.now().minusMonths(1);
        closed.setStatus(CampaignStatus.CLOSED);
        closed.setClosedAt(closedAt);
        when(campaigns.findByNgoIdAndDeletedFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(active, closed));

        service.closeAndHideAllOf(1L);

        verify(donations).cancelPendingByCampaign(4L);
        verify(donations).cancelPendingByCampaign(5L);
        assertThat(active.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(active.getClosedAt()).isNotNull();
        assertThat(closed.getClosedAt()).isEqualTo(closedAt);
        assertThat(List.of(active, closed)).allSatisfy(c -> {
            assertThat(c.isDeleted()).isTrue();
            assertThat(c.isRemovedByModeration()).isFalse();
        });
        // Exclusão lógica mesmo sem doações: o histórico de quem doou continua legível
        verify(campaigns).save(active);
        verify(campaigns).save(closed);
        verify(campaigns, never()).delete(any());
    }

    // ===================== Apoio =====================

    private void givenLogged(User user) {
        when(users.findByEmail(NGO_EMAIL)).thenReturn(Optional.of(user));
    }

    private void givenSaved() {
        when(campaigns.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(10L);
            }
            return c;
        });
    }

    private static Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private static User ngo(Long id) {
        return User.builder().id(id).name("Patinhas").email(NGO_EMAIL).userType(UserType.NGO).active(true).verified(true).build();
    }

    private static Campaign campaign(Long id, Long ngoId, String title) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setNgoId(ngoId);
        c.setTitle(title);
        c.setDescription("Descrição");
        c.setCategory(CampaignCategory.FOOD);
        c.setGoalAmount(new BigDecimal("1000.00"));
        c.setStatus(CampaignStatus.ACTIVE);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private static CampaignRequestDTO request(CampaignCategory category, String customCategory, boolean urgent) {
        return new CampaignRequestDTO("Cirurgia do Rex", "Rex precisa de cirurgia", category, customCategory,
                urgent, 8000L, null, null);
    }
}
