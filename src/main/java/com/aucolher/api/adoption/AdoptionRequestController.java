package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.ChangeRequestStatusDTO;
import com.aucolher.api.adoption.dto.NewAdoptionRequestDTO;
import com.aucolher.api.adoption.dto.RegisterReturnDTO;
import com.aucolher.api.adoption.dto.RehomeDTO;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pedidos de adoção da conta logada. Todas as rotas exigem token; enviar um
 * pedido é só para pessoa ou ONG (regra na SecurityConfig).
 */
@RestController
@RequestMapping("/api/adoption-requests")
@RequiredArgsConstructor
public class AdoptionRequestController {

    private final AdoptionRequestService requestService;

    @PostMapping
    public ResponseEntity<AdoptionRequestDTO> create(@Valid @RequestBody NewAdoptionRequestDTO body,
                                                     @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.create(loggedUser.getUsername(), body.animalId()));
    }

    /** Os pedidos que a conta enviou ("Meus interesses"). */
    @GetMapping("/sent")
    public ResponseEntity<List<AdoptionRequestDTO>> listSent(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.listSent(loggedUser.getUsername()));
    }

    /** Os pedidos recebidos para os animais da conta. */
    @GetMapping("/received")
    public ResponseEntity<List<AdoptionRequestDTO>> listReceived(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.listReceived(loggedUser.getUsername()));
    }

    /** Um pedido — só para o adotante ou o dono do animal. */
    @GetMapping("/{id}")
    public ResponseEntity<AdoptionRequestDTO> get(@PathVariable Long id, @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.get(id, loggedUser.getUsername()));
    }

    /**
     * Aceitar, recusar, confirmar entrega, confirmar chegada ou cancelar:
     * `{ "status": "ACCEPTED" }`. A Service confere o papel de quem pede e o
     * status de partida.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdoptionRequestDTO> changeStatus(@PathVariable Long id,
                                                           @Valid @RequestBody ChangeRequestStatusDTO body,
                                                           @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.changeStatus(id, loggedUser.getUsername(), body.status()));
    }

    /**
     * "Animal retornado": quem doou registra (ou corrige) que o animal voltou —
     * `{ "returnDate": "2026-11-03", "reason": "OTHER_PETS", "note": "..." }`.
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<AdoptionRequestDTO> registerReturn(@PathVariable Long id,
                                                             @RequestBody RegisterReturnDTO body,
                                                             @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.registerReturn(id, loggedUser.getUsername(), body));
    }

    /** "Republicar anúncio": o anúncio original no formato do cadastro, para o formulário abrir preenchido. */
    @GetMapping("/{id}/rehome-draft")
    public ResponseEntity<AnimalRequestDTO> getRehomeDraft(@PathVariable Long id, @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.getRehomeDraft(id, loggedUser.getUsername()));
    }

    /** O adotante anuncia o animal para uma nova adoção: `{ "reason", "note", "animal": { ...cadastro } }`. */
    @PostMapping("/{id}/rehome")
    public ResponseEntity<AdoptionRequestDTO> rehome(@PathVariable Long id,
                                                     @Valid @RequestBody RehomeDTO body,
                                                     @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.rehome(id, loggedUser.getUsername(), body));
    }

    /** "Desisti de doar": desfaz o repasse enquanto o anúncio novo não gerou uma adoção. */
    @PostMapping("/{id}/rehome/undo")
    public ResponseEntity<AdoptionRequestDTO> undoRehome(@PathVariable Long id, @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(requestService.undoRehome(id, loggedUser.getUsername()));
    }
}
