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
