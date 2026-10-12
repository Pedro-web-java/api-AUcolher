package com.aucolher.api.event.dto;

import java.time.LocalDateTime;

public record AttendeeDTO(AttendeeUserDTO user, LocalDateTime confirmedAt) {
}
