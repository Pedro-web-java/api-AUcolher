package com.aucolher.api.animal;

import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * As listagens carregam o dono junto (@EntityGraph) — sem isso, montar cada
 * card dispararia uma consulta extra por animal para buscar nome e cidade do dono.
 */
public interface AnimalRepository extends JpaRepository<Animal, Long>, JpaSpecificationExecutor<Animal> {

    /** Listagem pública com filtros (ver AnimalSpecifications). */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Animal> findAll(Specification<Animal> spec, Pageable pageable);

    /** "Meus animais": todos os anúncios da conta, em qualquer status. */
    @EntityGraph(attributePaths = "owner")
    List<Animal> findByOwnerEmailOrderByCreatedAtDesc(String email);

    /** Animais de um perfil público (só os disponíveis). */
    @EntityGraph(attributePaths = "owner")
    List<Animal> findByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, AnimalStatus status);

    /**
     * Candidatos do deck do AUmatch: os disponíveis de contas ativas, fora os
     * anúncios de quem está vendo. A ordem vem do score, calculado na MatchService.
     */
    @EntityGraph(attributePaths = "owner")
    List<Animal> findByStatusAndOwnerActiveTrueAndOwnerIdNot(AnimalStatus status, Long ownerId);

    /**
     * Cidades (do dono) que têm animal na vitrine, para o filtro de cidade:
     * as mesmas regras base da listagem pública (disponível e conta ativa).
     */
    @Query("SELECT DISTINCT a.owner.city FROM Animal a WHERE a.status = :status AND a.owner.active = true "
            + "AND a.owner.city IS NOT NULL AND a.owner.city <> '' ORDER BY a.owner.city")
    List<String> findListedOwnerCities(AnimalStatus status);

    /** Anúncios de uma conta, em qualquer status (painel do admin). */
    long countByOwnerId(Long ownerId);

    /** Painel do admin: todos os anúncios, em qualquer status, dos mais recentes para os mais antigos. */
    @EntityGraph(attributePaths = "owner")
    List<Animal> findAllByOrderByCreatedAtDescIdDesc();

    /** Quantos anúncios cada conta tem, em qualquer status, numa consulta só. */
    @Query("SELECT a.owner.id AS ownerId, count(a) AS total FROM Animal a GROUP BY a.owner.id")
    List<OwnerCount> countByOwner();

    interface OwnerCount {
        Long getOwnerId();
        Long getTotal();
    }

    /** O anúncio republicado a partir de um pedido de adoção (repasse). */
    Optional<Animal> findByOriginRequestId(Long requestId);

    /** Os anúncios republicados de vários pedidos, numa consulta só: pedido → anúncio. */
    @Query("SELECT a.originRequestId AS requestId, a.id AS animalId FROM Animal a WHERE a.originRequestId IN :requestIds")
    List<RehomedListing> findRehomedListings(Collection<Long> requestIds);

    interface RehomedListing {
        Long getRequestId();
        Long getAnimalId();
    }

    /**
     * Só a foto de capa (ordem 0) de cada animal, numa consulta única. Os
     * cards não precisam das outras fotos, que podem ter centenas de KB cada.
     */
    @Query(value = "SELECT animal_id AS animalId, url FROM animal_photos WHERE sort_order = 0 AND animal_id IN (:ids)",
            nativeQuery = true)
    List<CoverPhoto> findCoverPhotos(Collection<Long> ids);

    interface CoverPhoto {
        Long getAnimalId();
        String getUrl();
    }
}
