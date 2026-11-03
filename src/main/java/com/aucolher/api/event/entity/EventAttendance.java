package com.aucolher.api.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "event_attendances")
@IdClass(EventAttendanceId.class)
@Getter
@NoArgsConstructor
public class EventAttendance {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    public EventAttendance(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.confirmedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
