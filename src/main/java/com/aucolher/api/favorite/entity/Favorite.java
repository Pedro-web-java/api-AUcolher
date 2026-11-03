package com.aucolher.api.favorite.entity;

import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Um animal salvo por um usuário — ligação usuário ↔ animal, única por par
 * (constraint uq_favorites_user_animal).
 *
 * Só é lida pelo JPA: a gravação é um INSERT ... ON CONFLICT DO NOTHING no
 * FavoriteRepository, então a data vem do DEFAULT do banco.
 */
@Entity
@Table(name = "favorites")
@Getter
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
