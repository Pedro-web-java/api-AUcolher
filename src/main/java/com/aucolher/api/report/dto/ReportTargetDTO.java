package com.aucolher.api.report.dto;

import com.aucolher.api.animal.entity.Species;
import com.aucolher.api.report.entity.Report;
import com.aucolher.api.user.entity.UserType;

/**
 * A cópia do alvo guardada na denúncia. ANIMAL: nome, foto, espécie e dono;
 * USER: nome, foto, tipo de conta, cidade e UF. O resto vem nulo.
 */
public record ReportTargetDTO(
        String name,
        String photoUrl,
        UserType userType,
        String city,
        String state,
        Species species,
        Long ownerId,
        String ownerName,
        UserType ownerUserType
) {

    public static ReportTargetDTO from(Report report) {
        return new ReportTargetDTO(
                report.getTargetName(),
                report.getTargetPhotoUrl(),
                report.getTargetUserType(),
                report.getTargetCity(),
                report.getTargetState(),
                report.getTargetSpecies(),
                report.getTargetOwnerId(),
                report.getTargetOwnerName(),
                report.getTargetOwnerUserType());
    }
}
