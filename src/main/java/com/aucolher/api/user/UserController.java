package com.aucolher.api.user;

import com.aucolher.api.user.dto.AccountDeletionDTO;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import com.aucolher.api.user.dto.PublicProfileDTO;
import com.aucolher.api.user.dto.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    /** Público: página de perfil de uma ONG ou pessoa, sem e-mail de login. */
    @GetMapping("/{id}/public-profile")
    public ResponseEntity<PublicProfileDTO> getPublicProfile(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserDetails viewer) {
        return ResponseEntity.ok(userService.getPublicProfile(id, viewer == null ? null : viewer.getUsername()));
    }

    /**
     * "Minha conta": a conta é a do token (o e-mail vem no JWT), então não há
     * id na rota — ninguém consegue editar o perfil de outra pessoa.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateProfile(@AuthenticationPrincipal UserDetails loggedUser,
                                                              @Valid @RequestBody ProfileUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(loggedUser.getUsername(), dto));
    }

    /**
     * Exclui a conta logada, sem volta. POST, e não DELETE, porque DELETE com
     * corpo é mal suportado por proxies e clientes HTTP. Depois disso o token
     * da conta para de valer.
     */
    @PostMapping("/me/deletion")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserDetails loggedUser,
                                              @RequestBody(required = false) AccountDeletionDTO body) {
        accountDeletionService.deleteAccount(loggedUser.getUsername(), body);
        return ResponseEntity.noContent().build();
    }
}
