package com.aucolher.api.report.dto;

import com.aucolher.api.report.entity.ReportReason;
import com.aucolher.api.report.entity.ReportTargetType;
import com.aucolher.api.shared.validation.Sanitizer;

/**
 * Corpo do POST /api/reports. Sem anotações de validação: as frases do
 * contrato (com ponto final) saem da Service, que confere também se o motivo
 * vale para o alvo.
 */
public record NewReportDTO(
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String description
) {

    public NewReportDTO {
        description = Sanitizer.text(description);
    }
}
