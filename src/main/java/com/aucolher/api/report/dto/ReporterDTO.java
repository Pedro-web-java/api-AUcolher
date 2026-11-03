package com.aucolher.api.report.dto;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;

/** Quem denunciou — só o admin vê. */
public record ReporterDTO(Long id, String name, String photoUrl, UserType userType) {

    public static ReporterDTO from(User reporter) {
        return new ReporterDTO(reporter.getId(), reporter.getName(), reporter.getPhotoUrl(), reporter.getUserType());
    }
}
