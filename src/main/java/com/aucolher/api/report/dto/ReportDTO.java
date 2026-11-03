package com.aucolher.api.report.dto;

import com.aucolher.api.report.entity.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Uma denúncia. Para o admin (AdminReportDTO do contrato) vem também
 * `reporter`; para quem denunciou, o campo nem aparece.
 */
public record ReportDTO(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        ReportTargetDTO target,
        ReportReason reason,
        String description,
        ReportStatus status,
        ReportResolution resolution,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReporterDTO reporter
) {

    /** A visão de quem denunciou. */
    public static ReportDTO mine(Report report) {
        return of(report, null);
    }

    /** A visão do admin, com quem denunciou. */
    public static ReportDTO forAdmin(Report report) {
        return of(report, ReporterDTO.from(report.getReporter()));
    }

    private static ReportDTO of(Report report, ReporterDTO reporter) {
        return new ReportDTO(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                ReportTargetDTO.from(report),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getResolution(),
                report.getCreatedAt(),
                report.getResolvedAt(),
                reporter);
    }
}
