package com.aucolher.api.adoption.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Avaliação de uma adoção concluída (uq_adoption_reviews_request_role: uma
 * por lado). O autor e o avaliado saem do pedido e do papel — o adotante
 * avalia o dono do animal e vice-versa.
 */
@Entity
@Table(name = "adoption_reviews")
@Getter
@Setter
@NoArgsConstructor
public class AdoptionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private AdoptionRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10)
    private ReviewRole authorRole;

    /** Estrelas, de 1 a 5. */
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Preenchida só quando o autor edita. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public AdoptionReview(AdoptionRequest request, ReviewRole authorRole) {
        this.request = request;
        this.authorRole = authorRole;
    }

    @PrePersist
    protected void onPersist() {
        createdAt = LocalDateTime.now();
    }

    /** Nota e comentário novos; a data de edição só aparece numa avaliação que já existia. */
    public void edit(Integer rating, String comment) {
        if (id != null) {
            updatedAt = LocalDateTime.now();
        }
        this.rating = rating;
        this.comment = comment;
    }
}
