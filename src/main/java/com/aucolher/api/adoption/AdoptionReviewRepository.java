package com.aucolher.api.adoption;

import com.aucolher.api.adoption.entity.AdoptionReview;
import com.aucolher.api.adoption.entity.ReviewRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Avaliações das adoções concluídas. */
public interface AdoptionReviewRepository extends JpaRepository<AdoptionReview, Long> {

    Optional<AdoptionReview> findByRequestIdAndAuthorRole(Long requestId, ReviewRole authorRole);

    /** As avaliações de vários pedidos de uma vez, para montar as listas. */
    List<AdoptionReview> findByRequestIdIn(Collection<Long> requestIds);

    /**
     * O que disseram sobre uma conta: as avaliações dos adotantes nos
     * pedidos dos animais dela e as dos donos nos pedidos que ela fez.
     */
    @EntityGraph(attributePaths = {"request", "request.animal", "request.animal.owner", "request.adopter"})
    @Query("SELECT r FROM AdoptionReview r"
            + " WHERE (r.authorRole = :adopterRole AND r.request.animal.owner.id = :userId)"
            + " OR (r.authorRole = :ownerRole AND r.request.adopter.id = :userId)"
            + " ORDER BY r.createdAt DESC, r.id DESC")
    List<AdoptionReview> findAboutUser(Long userId, ReviewRole adopterRole, ReviewRole ownerRole);
}
