package com.aucolher.api.user;

import com.aucolher.api.adoption.AdoptionRequestService;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.event.EventAttendanceRepository;
import com.aucolher.api.event.EventService;
import com.aucolher.api.favorite.FavoriteRepository;
import com.aucolher.api.match.MatchProfileRepository;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.report.entity.ReportResolution;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.user.dto.AccountDeletionDTO;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Excluir a própria conta (api-exclusao-conta.md), com o banco e os outros módulos simulados (Mockito). */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    static final String HASH = "$2a$10$hash";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AdoptionRequestService requestService;
    @Mock
    private AnimalService animalService;
    @Mock
    private CampaignService campaignService;
    @Mock
    private EventService eventService;
    @Mock
    private MatchProfileRepository matchProfileRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private EventAttendanceRepository attendanceRepository;
    @Mock
    private ReportService reportService;

    @InjectMocks
    private AccountDeletionService service;

    // ===================== Recusas =====================

    @Test
    void adminCannotDeleteTheirAccount() {
        User admin = User.builder().id(1L).email("admin@email.com").password(HASH).userType(UserType.ADMIN).build();
        when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.deleteAccount("admin@email.com", new AccountDeletionDTO("senha123", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("A conta de administrador não pode ser excluída.");
        assertThat(admin.isDeleted()).isFalse();
        verifyNoInteractions(requestService, animalService, campaignService, eventService, reportService);
    }

    @Test
    void accountWithPasswordConfirmsWithTheRightPassword() {
        User person = givenAccount(person());
        when(passwordEncoder.matches("errada", HASH)).thenReturn(false);

        assertRefused(person, new AccountDeletionDTO("errada", null), "Senha incorreta.");
        assertRefused(person, new AccountDeletionDTO(null, null), "Senha incorreta.");
        assertRefused(person, new AccountDeletionDTO(null, "EXCLUIR"), "Senha incorreta.");
        assertRefused(person, null, "Senha incorreta.");
    }

    @Test
    void googleAccountWithoutPasswordTypesExcluir() {
        User google = givenAccount(person());
        google.setPassword(null);
        google.setProvider(AuthProvider.GOOGLE);

        assertRefused(google, new AccountDeletionDTO(null, "excluir"), "Digite EXCLUIR para confirmar.");
        assertRefused(google, new AccountDeletionDTO("senha123", null), "Digite EXCLUIR para confirmar.");
        assertRefused(google, null, "Digite EXCLUIR para confirmar.");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void googleAccountIsDeletedWithExcluir() {
        User google = givenAccount(person());
        google.setPassword(null);
        google.setProvider(AuthProvider.GOOGLE);
        when(userRepository.findById(5L)).thenReturn(Optional.of(google));

        service.deleteAccount(google.getEmail(), new AccountDeletionDTO(null, " EXCLUIR "));

        assertThat(google.isDeleted()).isTrue();
    }

    // ===================== Efeitos =====================

    @Test
    void personDeletionCancelsTheRequestsBeforeTakingTheAnimalsOffline() {
        User person = givenAccount(person());
        when(passwordEncoder.matches("senha123", HASH)).thenReturn(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(person));

        service.deleteAccount("person@email.com", new AccountDeletionDTO("senha123", null));

        InOrder order = inOrder(requestService, animalService, userRepository, reportService);
        // Antes dos animais, senão o "saiu da vitrine" cancelaria como ANIMAL_UNAVAILABLE
        order.verify(requestService).cancelAllForAccount(5L, CancelReason.ACCOUNT_DELETED);
        order.verify(animalService).unlistAllOf(5L);
        order.verify(userRepository).save(person);
        order.verify(reportService).resolveAgainstAccount(eq(5L), eq(ReportResolution.DISMISSED), isNull(),
                any(LocalDateTime.class));

        verify(matchProfileRepository).deleteById(5L);
        verify(favoriteRepository).removeAllOf(5L);
        verify(attendanceRepository).deleteAllOf(5L);

        assertThat(person.getName()).isEqualTo("Conta excluída");
        assertThat(person.getEmail()).isEqualTo("excluida-5@aucolher.invalid");
        assertThat(person.getPassword()).isNull();
        assertThat(person.getPhotoUrl()).isNull();
        assertThat(person.getCity()).isNull();
        assertThat(person.getActive()).isFalse();
        assertThat(person.getDeletedAt()).isNotNull();
    }

    @Test
    void ngoDeletionClosesTheCampaignsAndCancelsTheUpcomingEvents() {
        User ngo = givenAccount(User.builder().id(2L).name("ONG Patinhas").email("ngo@email.com").password(HASH)
                .userType(UserType.NGO).cnpj("11222333000181").verified(true).build());
        when(passwordEncoder.matches("senha123", HASH)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(ngo));

        service.deleteAccount("ngo@email.com", new AccountDeletionDTO("senha123", null));

        InOrder order = inOrder(requestService, animalService, campaignService, eventService, userRepository);
        order.verify(requestService).cancelAllForAccount(2L, CancelReason.ACCOUNT_DELETED);
        order.verify(animalService).unlistAllOf(2L);
        order.verify(campaignService).closeAndHideAllOf(2L);
        order.verify(eventService).cancelUpcomingOf(2L);
        order.verify(userRepository).save(ngo);

        // O CNPJ fica livre para outra ONG, e o selo sai
        assertThat(ngo.getCnpj()).isNull();
        assertThat(ngo.getVerified()).isFalse();
        assertThat(ngo.getUserType()).isEqualTo(UserType.NGO);
    }

    // ===================== Apoio =====================

    private void assertRefused(User account, AccountDeletionDTO body, String message) {
        assertThatThrownBy(() -> service.deleteAccount(account.getEmail(), body))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
        assertThat(account.isDeleted()).isFalse();
        verifyNoInteractions(requestService, animalService, campaignService, eventService, reportService);
    }

    private User givenAccount(User account) {
        when(userRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        return account;
    }

    private static User person() {
        return User.builder().id(5L).name("Maria").email("person@email.com").password(HASH)
                .userType(UserType.PERSON).photoUrl("foto").city("Araranguá").state("SC").build();
    }
}
