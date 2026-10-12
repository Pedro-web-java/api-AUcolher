package com.aucolher.api.favorite;

import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Favoritos da conta logada — todas as rotas exigem token. */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** Cards dos animais favoritados, do mais recente para o mais antigo. */
    @GetMapping
    public ResponseEntity<List<AnimalSummaryDTO>> list(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(favoriteService.list(loggedUser.getUsername()));
    }

    /** Só os ids dos animais favoritados. */
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> listIds(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(favoriteService.listIds(loggedUser.getUsername()));
    }

    /** PUT porque é idempotente: repetir a chamada não cria um segundo favorito. */
    @PutMapping("/{animalId}")
    public ResponseEntity<Void> add(@PathVariable Long animalId, @AuthenticationPrincipal UserDetails loggedUser) {
        favoriteService.add(loggedUser.getUsername(), animalId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{animalId}")
    public ResponseEntity<Void> remove(@PathVariable Long animalId, @AuthenticationPrincipal UserDetails loggedUser) {
        favoriteService.remove(loggedUser.getUsername(), animalId);
        return ResponseEntity.noContent().build();
    }
}
