package com.aucolher.api.password;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aucolher.api.password.dto.ForgotPasswordDTO;
import com.aucolher.api.password.dto.ResetPasswordDTO;
import com.aucolher.api.password.dto.ResetTokenDTO;

/** Rotas públicas (já cobertas por /api/auth/** no SecurityConfig). POST de propósito: o token não vai em URL. */
@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@RequestBody(required = false) ForgotPasswordDTO body) {
        service.forgot(body == null ? null : body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset/check")
    public ResponseEntity<Void> check(@RequestBody(required = false) ResetTokenDTO body) {
        service.checkToken(body == null ? null : body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@RequestBody(required = false) ResetPasswordDTO body) {
        service.reset(body == null ? null : body.token(), body == null ? null : body.newPassword());
        return ResponseEntity.noContent().build();
    }
}
