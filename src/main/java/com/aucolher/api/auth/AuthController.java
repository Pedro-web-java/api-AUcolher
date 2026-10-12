package com.aucolher.api.auth;

import com.aucolher.api.auth.dto.AuthResponseDTO;
import com.aucolher.api.auth.dto.NgoRegistrationDTO;
import com.aucolher.api.auth.dto.PersonRegistrationDTO;
import com.aucolher.api.auth.dto.LoginDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/ngo")
    public ResponseEntity<AuthResponseDTO> registerNgo(@Valid @RequestBody NgoRegistrationDTO dto) {
        AuthResponseDTO response = authService.registerNgo(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register/person")
    public ResponseEntity<AuthResponseDTO> registerPerson(@Valid @RequestBody PersonRegistrationDTO dto) {
        AuthResponseDTO response = authService.registerPerson(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Rota única de login — vale para ONG e usuário comum; o tipo vem em user.userType. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginDTO dto) {
        AuthResponseDTO response = authService.login(dto);
        return ResponseEntity.ok(response);
    }
}
