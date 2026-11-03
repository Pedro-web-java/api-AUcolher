package com.aucolher.api.report;

import com.aucolher.api.report.dto.NewReportDTO;
import com.aucolher.api.report.dto.ReportDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Denúncias feitas no site. Exigem login; o admin não denuncia (403 com a
 * frase do contrato, dada pela Service). O julgamento fica no AdminController.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** `{ "targetType": "ANIMAL", "targetId": 8, "reason": "SCAM", "description": "..." }` */
    @PostMapping
    public ResponseEntity<ReportDTO> create(@RequestBody NewReportDTO body, @AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(loggedUser.getUsername(), body));
    }

    /** As denúncias que a conta fez. */
    @GetMapping("/mine")
    public ResponseEntity<List<ReportDTO>> listMine(@AuthenticationPrincipal UserDetails loggedUser) {
        return ResponseEntity.ok(reportService.listMine(loggedUser.getUsername()));
    }
}
