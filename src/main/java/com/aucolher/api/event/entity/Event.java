package com.aucolher.api.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ngo_id", nullable = false)
    private Long ngoId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    /** Nome livre da categoria, só quando category = OTHER. */
    @Column(name = "custom_category", length = 30)
    private String customCategory;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "venue_name", nullable = false, length = 120)
    private String venueName;

    @Column(length = 8)
    private String cep;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    @Column(length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    private Integer capacity;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.ACTIVE;

    /** Cancelado pelo admin (sempre CANCELLED) — separado do que a própria ONG cancelou. */
    @Column(name = "removed_by_moderation", nullable = false)
    private boolean removedByModeration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Última edição feita pelo admin (nula se nunca houve): o dono vê "Editado pela moderação". */
    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    /** O admin que fez essa edição (só o id, como nas denúncias). */
    @Column(name = "moderated_by")
    private Long moderatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        }
    }

    /** O admin editou o conteúdo de outra pessoa: fica registrado quando e quem. */
    public void markModeratedBy(Long adminId) {
        moderatedAt = LocalDateTime.now();
        moderatedBy = adminId;
    }
}
