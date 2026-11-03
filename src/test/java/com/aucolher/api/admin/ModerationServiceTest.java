package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminUserDTO;
import com.aucolher.api.adoption.AdoptionRequestService;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.event.EventService;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.report.dto.ReportDTO;
import com.aucolher.api.report.entity.*;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Banir, reativar, remover e arquivar — as decisões do admin, com o banco simulado. */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    static final String ADMIN_EMAIL = "admin@email.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalService animalService;
    @Mock
    private EventService eventService;
    @Mock
    private ReportService reportService;
    @Mock
    private AdoptionRequestService requestService;

    @InjectMocks
    private ModerationService moderationService;

    @Test
    void banningSuspendsTheAccountResolvesReportsAndCancelsRequests() {
        User person = account(5L, UserType.PERSON);
        User admin = givenAdmin();
        when(userRepository.findById(5L)).thenReturn(Optional.of(person));
        when(animalRepository.countByOwnerId(5L)).thenReturn(2L);

        AdminUserDTO banned = moderationService.setUserActive(5L, false, ADMIN_EMAIL);

        assertThat(banned.active()).isFalse();
        assertThat(banned.animalsCount()).isEqualTo(2L);
        verify(reportService).resolveAgainstAccount(eq(5L), eq(ReportResolution.ACCOUNT_BANNED), eq(admin),
                any(LocalDateTime.class));
        verify(requestService).cancelAllForAccount(5L, CancelReason.ACCOUNT_BANNED);
    }

    @Test
    void reactivatingOnlyLetsTheAccountBackIn() {
        User person = account(5L, UserType.PERSON);
        person.setActive(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(person));

        assertThat(moderationService.setUserActive(5L, true, ADMIN_EMAIL).active()).isTrue();
        verifyNoInteractions(reportService, requestService);
    }

    @Test
    void aDeletedAccountIsNeverReactivated() {
        User deleted = account(5L, UserType.PERSON);
        deleted.anonymize(LocalDateTime.now());
        when(userRepository.findById(5L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> moderationService.setUserActive(5L, true, ADMIN_EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Contas excluídas não podem ser reativadas.");
        assertThat(deleted.getActive()).isFalse();
    }

    @Test
    void theAdminListShowsWhenTheAccountWasDeleted() {
        User deleted = account(5L, UserType.PERSON);
        LocalDateTime now = LocalDateTime.now();
        deleted.anonymize(now);

        AdminUserDTO dto = AdminUserDTO.from(deleted, 0);

        assertThat(dto.name()).isEqualTo("Conta excluída");
        assertThat(dto.active()).isFalse();
        assertThat(dto.deletedAt()).isEqualTo(now);
        assertThat(AdminUserDTO.from(account(6L, UserType.PERSON), 0).deletedAt()).isNull();
    }

    @Test
    void statusIsRequiredAndAdminsCannotBeBanned() {
        assertThatThrownBy(() -> moderationService.setUserActive(5L, null, ADMIN_EMAIL))
                .hasMessage("Informe se a conta fica ativa.");

        when(userRepository.findById(9L)).thenReturn(Optional.of(account(9L, UserType.ADMIN)));
        givenAdmin();
        assertThatThrownBy(() -> moderationService.setUserActive(9L, false, ADMIN_EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Contas de admin não podem ser banidas.");

        when(userRepository.findById(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> moderationService.setUserActive(77L, false, ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conta não encontrada.");
    }

    @Test
    void banningByAListingReportBansItsOwner() {
        Report report = report(ReportTargetType.ANIMAL, 8L);
        report.setTargetOwnerId(2L);
        User owner = account(2L, UserType.NGO);
        User admin = givenAdmin();
        when(reportService.findReport(12L)).thenReturn(report);
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(reportService.resolveAgainstAccount(eq(2L), eq(ReportResolution.ACCOUNT_BANNED), eq(admin), any()))
                .thenAnswer(invocation -> {
                    report.resolve(ReportResolution.ACCOUNT_BANNED, admin, invocation.getArgument(3));
                    return List.of(report);
                });

        List<ReportDTO> resolved = moderationService.banByReport(12L, ADMIN_EMAIL);

        assertThat(owner.getActive()).isFalse();
        assertThat(resolved).singleElement().satisfies(dto -> {
            assertThat(dto.resolution()).isEqualTo(ReportResolution.ACCOUNT_BANNED);
            assertThat(dto.reporter().id()).isEqualTo(1L);
        });
    }

    @Test
    void aJudgedReportCannotBeJudgedAgain() {
        Report report = report(ReportTargetType.USER, 5L);
        report.resolve(ReportResolution.DISMISSED, null, LocalDateTime.now());
        when(reportService.findReport(12L)).thenReturn(report);

        assertThatThrownBy(() -> moderationService.dismiss(12L, ADMIN_EMAIL)).hasMessage("Esta denúncia já foi julgada.");
        assertThatThrownBy(() -> moderationService.banByReport(12L, ADMIN_EMAIL)).hasMessage("Esta denúncia já foi julgada.");
    }

    @Test
    void reportWithoutAnAccountCannotBan() {
        when(reportService.findReport(12L)).thenReturn(report(ReportTargetType.ANIMAL, 8L));

        assertThatThrownBy(() -> moderationService.banByReport(12L, ADMIN_EMAIL))
                .hasMessage("Não foi possível identificar a conta denunciada.");
    }

    @Test
    void dismissingResolvesTheWholeCase() {
        when(reportService.findReport(12L)).thenReturn(report(ReportTargetType.USER, 5L));
        User admin = givenAdmin();

        moderationService.dismiss(12L, ADMIN_EMAIL);

        verify(reportService).resolveCase(eq(ReportTargetType.USER), eq(5L), eq(ReportResolution.DISMISSED), eq(admin), any());
    }

    @Test
    void removingAListingResolvesItsReports() {
        User admin = givenAdmin();

        moderationService.removeAnimal(8L, ADMIN_EMAIL);

        verify(animalService).removeByModeration(8L);
        verify(reportService).resolveCase(eq(ReportTargetType.ANIMAL), eq(8L), eq(ReportResolution.LISTING_REMOVED), eq(admin), any());
    }

    // ===================== Fixtures =====================

    private User givenAdmin() {
        User admin = account(9L, UserType.ADMIN);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        return admin;
    }

    private static User account(Long id, UserType type) {
        return User.builder().id(id).email("user" + id + "@email.com").name("Conta " + id).userType(type).build();
    }

    private static Report report(ReportTargetType type, Long targetId) {
        Report report = new Report();
        report.setId(12L);
        report.setReporter(account(1L, UserType.PERSON));
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setTargetName("Alvo");
        report.setReason(ReportReason.SPAM);
        return report;
    }
}
