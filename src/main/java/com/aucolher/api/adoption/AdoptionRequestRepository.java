package com.aucolher.api.adoption;

import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Pedidos de adoção. As listagens carregam animal, dono e adotante juntos
 * (@EntityGraph) — sem isso, montar cada pedido dispararia consultas extras.
 */
public interface AdoptionRequestRepository extends JpaRepository<AdoptionRequest, Long> {

    /** "Meus interesses": os pedidos que a pessoa enviou, do mais recente para o mais antigo. */
    @EntityGraph(attributePaths = {"animal", "animal.owner", "adopter"})
    List<AdoptionRequest> findByAdopterIdOrderByCreatedAtDescIdDesc(Long adopterId);

    /** Pedidos recebidos: os dos animais da conta. */
    @EntityGraph(attributePaths = {"animal", "animal.owner", "adopter"})
    List<AdoptionRequest> findByAnimalOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    @EntityGraph(attributePaths = {"animal", "animal.owner", "adopter"})
    Optional<AdoptionRequest> findWithDetailsById(Long id);

    /** Painel do admin: todos os pedidos da plataforma. */
    @EntityGraph(attributePaths = {"animal", "animal.owner", "adopter"})
    List<AdoptionRequest> findAllByOrderByCreatedAtDescIdDesc();

    /** O pedido que impede um novo: qualquer um que não esteja cancelado. */
    Optional<AdoptionRequest> findFirstByAdopterIdAndAnimalIdAndStatusNot(Long adopterId, Long animalId, AdoptionStatus status);

    /**
     * Animais que a pessoa já pediu e não pode pedir de novo (qualquer pedido
     * não cancelado) — o deck do AUmatch esconde esses.
     */
    @Query("SELECT r.animal.id FROM AdoptionRequest r WHERE r.adopter.id = :adopterId AND r.status <> :ignored")
    List<Long> findRequestedAnimalIds(Long adopterId, AdoptionStatus ignored);

    /** Pedidos nos status dados em que a conta participou — como dona do animal ou como adotante. */
    @Query("SELECT count(r) FROM AdoptionRequest r WHERE r.status IN :statuses"
            + " AND (r.adopter.id = :userId OR r.animal.owner.id = :userId)")
    long countByStatusInvolving(Long userId, Collection<AdoptionStatus> statuses);

    /** Pedidos nos status dados em que a conta é adotante ou dona do animal — usado no banimento. */
    @Query("SELECT r FROM AdoptionRequest r WHERE r.status IN :statuses"
            + " AND (r.adopter.id = :userId OR r.animal.owner.id = :userId)")
    List<AdoptionRequest> findInvolving(Long userId, Collection<AdoptionStatus> statuses);

    /** Os animais com algum pedido nos status dados — o admin marca os "em andamento". */
    @Query("SELECT DISTINCT r.animal.id FROM AdoptionRequest r WHERE r.status IN :statuses")
    List<Long> findAnimalIdsWithStatus(Collection<AdoptionStatus> statuses);

    /** Pedidos de um animal nos status dados — usado para cancelar os ativos. */
    List<AdoptionRequest> findByAnimalIdAndStatusIn(Long animalId, Collection<AdoptionStatus> statuses);
}
