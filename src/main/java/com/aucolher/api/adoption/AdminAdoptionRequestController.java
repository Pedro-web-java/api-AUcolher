package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdminAdoptionRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Painel do admin: todos os pedidos da plataforma (/api/admin/** é só da conta ADMIN). */
@RestController
@RequestMapping("/api/admin/adoption-requests")
@RequiredArgsConstructor
public class AdminAdoptionRequestController {

    private final AdoptionRequestService requestService;

    /** Do mais recente para o mais antigo, em qualquer status. */
    @GetMapping
    public ResponseEntity<List<AdminAdoptionRequestDTO>> listAll() {
        return ResponseEntity.ok(requestService.listAllForAdmin());
    }
}
