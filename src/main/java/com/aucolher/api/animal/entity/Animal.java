package com.aucolher.api.animal.entity;

import com.aucolher.api.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Animal anunciado para adoção por uma ONG ou por um usuário comum.
 *
 * A localização não fica aqui: o animal está onde o dono está, então cidade
 * e UF vêm de {@link #owner}. Os campos seguem as etapas do cadastro no
 * frontend — dados básicos, saúde, comportamento/compatibilidade e anúncio.
 */
@Entity
@Table(name = "animals")
@Getter
@Setter
@NoArgsConstructor
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // ===================== Dados básicos =====================

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Species species;

    @Column(nullable = false, length = 60)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Column(name = "age_value", nullable = false)
    private Integer ageValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_unit", nullable = false, length = 10)
    private AgeUnit ageUnit;

    /** Calculada a partir da idade a cada gravação (ver computeAgeGroup) — não vem do cliente. */
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 10)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnimalSize size;

    // ===================== Saúde =====================

    @Column(nullable = false)
    private Boolean vaccinated = false;

    @Column(nullable = false)
    private Boolean neutered = false;

    @Column(nullable = false)
    private Boolean dewormed = false;

    @Column(name = "special_needs", nullable = false)
    private Boolean specialNeeds = false;

    // ===================== Comportamento e compatibilidade =====================

    @Enumerated(EnumType.STRING)
    @Column(name = "energy_level", nullable = false, length = 10)
    private Level energyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Temperament temperament;

    @Enumerated(EnumType.STRING)
    @Column(name = "independence_level", nullable = false, length = 10)
    private Level independenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "vocalization", nullable = false, length = 10)
    private Level vocalization;

    @Column(name = "good_with_children", nullable = false)
    private Boolean goodWithChildren;

    @Column(name = "good_with_dogs", nullable = false)
    private Boolean goodWithDogs;

    @Column(name = "good_with_cats", nullable = false)
    private Boolean goodWithCats;

    @Column(name = "apartment_friendly", nullable = false)
    private Boolean apartmentFriendly;

    // ===================== Anúncio =====================

    /** Frase curta do card da listagem. */
    @Column(nullable = false, length = 200)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String story;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnimalStatus status = AnimalStatus.AVAILABLE;

    /**
     * De 1 a 4 fotos; a primeira é a capa do card. A ordem da lista vira a
     * coluna "sort_order" (@OrderColumn), como a equipe no perfil da ONG.
     */
    @ElementCollection
    @CollectionTable(name = "animal_photos", joinColumns = @JoinColumn(name = "animal_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private List<String> photos = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onPersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        computeAgeGroup();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        computeAgeGroup();
    }

    /** Recalculada a cada gravação, assim nunca fica em desacordo com a idade informada. */
    private void computeAgeGroup() {
        ageGroup = AgeGroup.of(ageValue, ageUnit);
    }

    /** Compara pelo e-mail, que é o que vem no token. Visitante sem login (null) nunca é o dono. */
    public boolean isOwnedBy(String email) {
        return email != null && email.equals(owner.getEmail());
    }
}
