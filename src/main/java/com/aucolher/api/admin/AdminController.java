package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminAnimalDTO;
import com.aucolher.api.admin.dto.AdminStatsDTO;
import com.aucolher.api.admin.dto.AdminUserDTO;
import com.aucolher.api.admin.dto.AdminUserProfileDTO;
import com.aucolher.api.admin.dto.AdminUserUpdateDTO;
import com.aucolher.api.animal.dto.AnimalDetailDTO;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.campaign.dto.CampaignRequestDTO;
import com.aucolher.api.campaign.dto.CampaignResponseDTO;
import com.aucolher.api.event.dto.EventRequestDTO;
import com.aucolher.api.admin.dto.UserStatusDTO;
import com.aucolher.api.event.EventService;
import com.aucolher.api.event.dto.EventResponseDTO;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.report.dto.ReportDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Painel do admin. Toda a /api/admin/** é só da conta ADMIN (regra na
 * SecurityConfig): sem token, 401; logado sem ser admin, 403.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminStatsService statsService;
    private final ModerationService moderationService;
    private final EventService eventService;
    private final CampaignService campaignService;
    private final ReportService reportService;

    /** Números e gráficos da Visão geral, com a taxa de reabandono. */
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDTO> stats() {
        return ResponseEntity.ok(statsService.stats());
    }

    // ===================== Listas =====================

    /** Contas de pessoas e ONGs (sem outros admins), mais recentes primeiro. */
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDTO>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    /** Todos os anúncios, em qualquer status, com `listingStatus` e `owner.active`. */
    @GetMapping("/animals")
    public ResponseEntity<List<AdminAnimalDTO>> listAnimals() {
        return ResponseEntity.ok(adminService.listAnimals());
    }

    /** Todos os eventos, da data mais distante para a mais antiga, com `ngo.active` e `removedByModeration`. */
    @GetMapping("/events")
    public ResponseEntity<List<EventResponseDTO>> listEvents() {
        return ResponseEntity.ok(eventService.listForAdmin());
    }

    /** Todas as campanhas (ativas, encerradas e excluídas), mais recentes primeiro, com `removed` e `ngo.active`. */
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignResponseDTO>> listCampaigns() {
        return ResponseEntity.ok(campaignService.listForAdmin());
    }

    /** Todas as denúncias, em qualquer situação, com quem denunciou. */
    @GetMapping("/reports")
    public ResponseEntity<List<ReportDTO>> listReports() {
        return ResponseEntity.ok(reportService.listAllForAdmin());
    }

    // ===================== Edição (o conteúdo segue em nome do dono) =====================

    /** A conta inteira de uma pessoa ou ONG (com o e-mail de login), para editar. */
    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserProfileDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.getUser(id));
    }

    /** `{ "email": "...", "profile": { ...o mesmo corpo de PUT /api/users/me } }` */
    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserProfileDTO> updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateDTO body,
                                                          @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.updateUser(id, body, admin.getUsername()));
    }

    /** O mesmo corpo de PUT /api/animals/{id}. */
    @PutMapping("/animals/{id}")
    public ResponseEntity<AnimalDetailDTO> updateAnimal(@PathVariable Long id, @Valid @RequestBody AnimalRequestDTO body,
                                                        @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.updateAnimal(id, body, admin.getUsername()));
    }

    /** O mesmo corpo de PUT /api/events/{id}. */
    @PutMapping("/events/{id}")
    public ResponseEntity<EventResponseDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody EventRequestDTO body,
                                                        @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.updateEvent(id, body, admin.getUsername()));
    }

    /** O mesmo corpo de PUT /api/campaigns/{id}. */
    @PutMapping("/campaigns/{id}")
    public ResponseEntity<CampaignResponseDTO> updateCampaign(@PathVariable Long id, @Valid @RequestBody CampaignRequestDTO body,
                                                              @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.updateCampaign(id, body, admin.getUsername()));
    }

    // ===================== Moderação =====================

    /** Banir (`{ "active": false }`) ou reativar (`{ "active": true }`) uma conta. */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<AdminUserDTO> setUserStatus(@PathVariable Long id, @RequestBody UserStatusDTO body,
                                                      @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.setUserActive(id, body.active(), admin.getUsername()));
    }

    /** Arquiva esta denúncia e as outras pendentes do mesmo alvo; devolve as que foram arquivadas. */
    @PostMapping("/reports/{id}/dismiss")
    public ResponseEntity<List<ReportDTO>> dismissReports(@PathVariable Long id, @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.dismiss(id, admin.getUsername()));
    }

    /** Bane a conta denunciada (perfil) ou a dona do anúncio denunciado; devolve as denúncias resolvidas. */
    @PostMapping("/reports/{id}/ban")
    public ResponseEntity<List<ReportDTO>> banReportedAccount(@PathVariable Long id, @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(moderationService.banByReport(id, admin.getUsername()));
    }

    @DeleteMapping("/animals/{id}")
    public ResponseEntity<Void> removeAnimal(@PathVariable Long id, @AuthenticationPrincipal UserDetails admin) {
        moderationService.removeAnimal(id, admin.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> removeEvent(@PathVariable Long id) {
        moderationService.removeEvent(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> removeCampaign(@PathVariable Long id) {
        moderationService.removeCampaign(id);
        return ResponseEntity.noContent().build();
    }
}
