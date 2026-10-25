package com.aucolher.api.match.entity;

import com.aucolher.api.animal.entity.AnimalSize;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Perfil AUmatch: as respostas do quiz de uma pessoa, uma linha por conta
 * (a chave é o próprio id do usuário).
 *
 * Toda resposta pode ficar vazia — "Minha conta" salva respostas soltas — e
 * o quiz só conta como completo com as 7 perguntas respondidas. A pergunta
 * "Quem mora com você?" vira os três booleanos has*: todos nulos (sem
 * resposta) ou todos preenchidos ("Ninguém" = três false).
 */
@Entity
@Table(name = "match_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MatchProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    // ===================== Estilo de vida =====================

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Housing housing;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_routine", length = 20)
    private ExerciseRoutine exerciseRoutine;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_away", length = 20)
    private TimeAway timeAway;

    @Column(name = "has_children")
    private Boolean hasChildren;

    @Column(name = "has_dogs")
    private Boolean hasDogs;

    @Column(name = "has_cats")
    private Boolean hasCats;

    // ===================== Preferências para o pet =====================

    @Enumerated(EnumType.STRING)
    @Column(name = "species_preference", length = 10)
    private SpeciesPreference speciesPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "ideal_pet_profile", length = 30)
    private IdealPetProfile idealPetProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_size", length = 10)
    private AnimalSize preferredSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MatchProfile(Long userId) {
        this.userId = userId;
    }

    @PrePersist
    protected void onPersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** "Quem mora com você?" respondida — os três booleanos andam juntos. */
    public boolean hasHouseholdAnswer() {
        return hasChildren != null;
    }

    /** As 7 perguntas do quiz respondidas. */
    public boolean isComplete() {
        return housing != null
                && exerciseRoutine != null
                && timeAway != null
                && hasHouseholdAnswer()
                && speciesPreference != null
                && idealPetProfile != null
                && preferredSize != null;
    }
}
