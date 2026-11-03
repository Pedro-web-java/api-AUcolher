package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.SaveReviewDTO;
import com.aucolher.api.adoption.dto.UserReviewsDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Avaliações: escrever e apagar exigem login (e ser parte do pedido); o
 * resumo de um perfil é público, como a vitrine.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdoptionReviewController {

    private final AdoptionReviewService reviewService;

    /** PUT porque é a minha avaliação daquele pedido: a primeira vez cria, depois edita. */
    @PutMapping("/adoption-requests/{id}/review")
    public ResponseEntity<AdoptionRequestDTO> save(@PathVariable Long id,
                                                   @Valid @RequestBody SaveReviewDTO body,
                                                   @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(reviewService.saveMine(id, loggedUser.getUsername(), body));
    }

    @DeleteMapping("/adoption-requests/{id}/review")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails loggedUser) {
        reviewService.deleteMine(id, loggedUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Média, distribuição, avaliações recebidas e adoções concluídas de um perfil. */
    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<UserReviewsDTO> summary(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.summaryOf(userId));
    }
}
