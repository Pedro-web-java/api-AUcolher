package com.aucolher.api.event.dto;

import com.aucolher.api.event.entity.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record EventResponseDTO(
        Long id,
        String title,
        String description,
        String category,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String venueName,
        String cep,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state,
        Integer capacity,
        long attendeesCount,
        String coverUrl,
        String status,
        LocalDateTime createdAt,
        NgoSummaryDTO ngo
) {
    public static EventResponseDTO from(Event e, long attendeesCount, NgoSummaryDTO ngo) {
        return new EventResponseDTO(
                e.getId(), e.getTitle(), e.getDescription(), e.getCategory().name(),
                e.getDate(), e.getStartTime(), e.getEndTime(), e.getVenueName(),
                e.getCep(), e.getStreet(), e.getNumber(), e.getComplement(), e.getDistrict(),
                e.getCity(), e.getState(), e.getCapacity(), attendeesCount,
                e.getCoverUrl(), e.getStatus().name(), e.getCreatedAt(), ngo);
    }
}
