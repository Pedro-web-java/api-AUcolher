package com.aucolher.api.user;

import com.aucolher.api.user.dto.ProfileUpdateDTO;
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

    /**
     * "Minha conta": a conta é a do token (o e-mail vem no JWT), então não há
     * id na rota — ninguém consegue editar o perfil de outra pessoa.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateProfile(@AuthenticationPrincipal UserDetails loggedUser,
                                                              @Valid @RequestBody ProfileUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(loggedUser.getUsername(), dto));
    }
}
