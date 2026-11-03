package com.aucolher.api.report;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.animal.entity.Species;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regras das denúncias do site e das decisões do admin, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    static final String REPORTER_EMAIL = "person@email.com";

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void reportingAnAnimalCopiesTheListingAndItsOwner() {
        givenReporter();
        when(animalRepository.findById(8L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE, owner())));
        when(animalRepository.findCoverPhotos(List.of(8L))).thenReturn(List.of(cover(8L, "https://fotos/nino.jpg")));
        givenSaved();

        ReportDTO created = reportService.create(REPORTER_EMAIL,
                new NewReportDTO(ReportTargetType.ANIMAL, 8L, ReportReason.SCAM, "  Pediu PIX antes da visita.  "));

        assertThat(created.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(created.description()).isEqualTo("Pediu PIX antes da visita.");
        assertThat(created.target().name()).isEqualTo("Nino");
        assertThat(created.target().photoUrl()).isEqualTo("https://fotos/nino.jpg");
        assertThat(created.target().species()).isEqualTo(Species.DOG);
        assertThat(created.target().ownerId()).isEqualTo(2L);
        assertThat(created.target().ownerUserType()).isEqualTo(UserType.NGO);
        assertThat(created.target().userType()).isNull();
        assertThat(created.reporter()).isNull();
    }

    @Test
    void reportingAProfileCopiesTheAccount() {
        givenReporter();
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner()));
        givenSaved();

        ReportDTO created = reportService.create(REPORTER_EMAIL,
                new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.FAKE_PROFILE, null));

        assertThat(created.target().name()).isEqualTo("Apacri");
        assertThat(created.target().userType()).isEqualTo(UserType.NGO);
        assertThat(created.target().city()).isEqualTo("Tubarão");
        assertThat(created.target().ownerId()).isNull();
    }

    @Test
    void adminDoesNotReport() {
        when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@email.com").userType(UserType.ADMIN).build()));

        assertThatThrownBy(() -> reportService.create("admin@email.com", new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.SPAM, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Contas de admin não fazem denúncias: a moderação é feita pelo painel.");
    }

    @Test
    void bodyIsCheckedWithTheContractMessages() {
        givenReporter();

        assertCreateFails(new NewReportDTO(ReportTargetType.ANIMAL, 8L, null, null), "Escolha o motivo da denúncia.");
        assertCreateFails(new NewReportDTO(ReportTargetType.ANIMAL, 8L, ReportReason.FAKE_PROFILE, null), "Escolha o motivo da denúncia.");
        assertCreateFails(new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.OTHER, "   "),
                "Conte o que aconteceu: a descrição é obrigatória em \"Outro motivo\".");
        assertCreateFails(new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.SPAM, "x".repeat(501)),
                "A descrição pode ter no máximo 500 caracteres.");
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void nobodyReportsTheirOwnContent() {
        User reporter = givenReporter();
        when(animalRepository.findById(8L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE, reporter)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertCreateFails(new NewReportDTO(ReportTargetType.ANIMAL, 8L, ReportReason.SPAM, null),
                "Você não pode denunciar o seu próprio anúncio.");
        assertCreateFails(new NewReportDTO(ReportTargetType.USER, 1L, ReportReason.SPAM, null),
                "Você não pode denunciar o seu próprio perfil.");
    }

    @Test
    void hiddenTargetsAreNotFound() {
        givenReporter();
        when(animalRepository.findById(8L)).thenReturn(Optional.of(animal(AnimalStatus.REMOVED, owner())));
        User banned = owner();
        banned.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> reportService.create(REPORTER_EMAIL, new NewReportDTO(ReportTargetType.ANIMAL, 8L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Anúncio não encontrado.");
        assertThatThrownBy(() -> reportService.create(REPORTER_EMAIL, new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Perfil não encontrado.");
    }

    @Test
    void aPendingReportBlocksAnotherForTheSameTarget() {
        givenReporter();
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner()));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(1L, ReportTargetType.USER, 2L, ReportStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.create(REPORTER_EMAIL, new NewReportDTO(ReportTargetType.USER, 2L, ReportReason.SPAM, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Você já denunciou este perfil. A denúncia está em análise.");
    }

    @Test
    void twoClicksAtOnceAlsoGet409() {
        givenReporter();
        when(animalRepository.findById(8L)).thenReturn(Optional.of(animal(AnimalStatus.AVAILABLE, owner())));
        when(reportRepository.saveAndFlush(any(Report.class))).thenThrow(new DataIntegrityViolationException("uq_reports_pending"));

        assertThatThrownBy(() -> reportService.create(REPORTER_EMAIL, new NewReportDTO(ReportTargetType.ANIMAL, 8L, ReportReason.SPAM, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Você já denunciou este anúncio. A denúncia está em análise.");
    }

    @Test
    void resolvingACaseResolvesEveryPendingReportOfTheTarget() {
        Report first = pendingReport(1L);
        Report second = pendingReport(2L);
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(ReportTargetType.ANIMAL, 8L, ReportStatus.PENDING))
                .thenReturn(List.of(first, second));
        User admin = User.builder().id(9L).userType(UserType.ADMIN).build();
        LocalDateTime now = LocalDateTime.now();

        List<Report> resolved = reportService.resolveCase(ReportTargetType.ANIMAL, 8L, ReportResolution.DISMISSED, admin, now);

        assertThat(resolved).allSatisfy(report -> {
            assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
            assertThat(report.getResolution()).isEqualTo(ReportResolution.DISMISSED);
            assertThat(report.getResolvedAt()).isEqualTo(now);
            assertThat(report.getResolvedBy()).isSameAs(admin);
        });
    }

    @Test
    void anAccountDeletingItselfArchivesTheReportsAgainstItWithNoAdmin() {
        Report report = pendingReport(1L);
        when(reportRepository.findAgainstAccount(2L, ReportStatus.PENDING)).thenReturn(List.of(report));
        LocalDateTime now = LocalDateTime.now();

        List<Report> resolved = reportService.resolveAgainstAccount(2L, ReportResolution.DISMISSED, null, now);

        assertThat(resolved).singleElement().satisfies(archived -> {
            assertThat(archived.getStatus()).isEqualTo(ReportStatus.RESOLVED);
            assertThat(archived.getResolution()).isEqualTo(ReportResolution.DISMISSED);
            assertThat(archived.getResolvedAt()).isEqualTo(now);
            assertThat(archived.getResolvedBy()).isNull();
        });
    }

    // ===================== Fixtures =====================

    private void assertCreateFails(NewReportDTO body, String message) {
        assertThatThrownBy(() -> reportService.create(REPORTER_EMAIL, body))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
    }

    private User givenReporter() {
        User reporter = User.builder().id(1L).email(REPORTER_EMAIL).name("Pessoa").userType(UserType.PERSON).build();
        when(userRepository.findByEmail(REPORTER_EMAIL)).thenReturn(Optional.of(reporter));
        return reporter;
    }

    private void givenSaved() {
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
    }

    static User owner() {
        return User.builder().id(2L).email("ngo@email.com").name("Apacri").userType(UserType.NGO)
                .city("Tubarão").state("SC").build();
    }

    static Animal animal(AnimalStatus status, User owner) {
        Animal animal = new Animal();
        animal.setId(8L);
        animal.setName("Nino");
        animal.setSpecies(Species.DOG);
        animal.setOwner(owner);
        animal.setStatus(status);
        return animal;
    }

    private static Report pendingReport(Long id) {
        Report report = new Report();
        report.setId(id);
        report.setTargetType(ReportTargetType.ANIMAL);
        report.setTargetId(8L);
        return report;
    }

    private static AnimalRepository.CoverPhoto cover(Long animalId, String url) {
        return new AnimalRepository.CoverPhoto() {
            public Long getAnimalId() { return animalId; }
            public String getUrl() { return url; }
        };
    }
}
