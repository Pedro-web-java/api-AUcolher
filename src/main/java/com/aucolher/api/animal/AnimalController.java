package com.aucolher.api.animal;

import com.aucolher.api.animal.dto.ChangeStatusDTO;
import com.aucolher.api.animal.dto.AnimalDetailDTO;
import com.aucolher.api.animal.dto.AnimalFilterDTO;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.shared.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rotas dos anúncios de animais. Mapeado em /api (e não em /api/animals)
 * porque também responde pelos animais de um perfil, em /api/users/{id}/animals.
 *
 * Nas rotas públicas, `loggedUser` vem null quando a requisição não traz token.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService animalService;

    /**
     * Público: vitrine de animais disponíveis, com filtros (ver AnimalFilterDTO)
     * e paginação — ?page=0&size=12 (página começa em 0; no máximo 50 por página).
     */
    @GetMapping("/animals")
    public ResponseEntity<PageDTO<AnimalSummaryDTO>> list(@ModelAttribute AnimalFilterDTO filter,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(animalService.listAvailable(filter, page, size));
    }

    /** Todos os anúncios do usuário logado, em qualquer status ("Minha conta"). */
    @GetMapping("/animals/mine")
    public ResponseEntity<List<AnimalSummaryDTO>> listMine(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(animalService.listMine(loggedUser.getUsername()));
    }

    /** Público: animais disponíveis de um perfil (página de perfil público). */
    @GetMapping("/users/{userId}/animals")
    public ResponseEntity<List<AnimalSummaryDTO>> listByOwner(@PathVariable Long userId) {
        return ResponseEntity.ok(animalService.listByOwner(userId));
    }

    @PostMapping("/animals")
    public ResponseEntity<AnimalDetailDTO> create(@AuthenticationPrincipal UserDetails loggedUser,
                                                      @Valid @RequestBody AnimalRequestDTO dto) {
        AnimalDetailDTO response = animalService.create(loggedUser.getUsername(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Público: qualquer visitante vê os detalhes de um animal. */
    @GetMapping("/animals/{id}")
    public ResponseEntity<AnimalDetailDTO> get(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(animalService.getDetail(id, emailOf(loggedUser)));
    }

    /** Edição completa do anúncio — só o dono. */
    @PutMapping("/animals/{id}")
    public ResponseEntity<AnimalDetailDTO> update(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails loggedUser,
                                                   @Valid @RequestBody AnimalRequestDTO dto) {
        return ResponseEntity.ok(animalService.update(id, loggedUser.getUsername(), dto));
    }

    /** Marcar como adotado, tirar do ar ou reativar — só o dono. */
    @PatchMapping("/animals/{id}/status")
    public ResponseEntity<AnimalDetailDTO> changeStatus(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserDetails loggedUser,
                                                          @Valid @RequestBody ChangeStatusDTO dto) {
        return ResponseEntity.ok(animalService.changeStatus(id, loggedUser.getUsername(), dto.status()));
    }

    /** Tira o anúncio do ar (exclusão lógica: vira INACTIVE) — só o dono. */
    @DeleteMapping("/animals/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails loggedUser) {
        animalService.deactivate(id, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    private static String emailOf(UserDetails loggedUser) {
        return loggedUser == null ? null : loggedUser.getUsername();
    }
}
