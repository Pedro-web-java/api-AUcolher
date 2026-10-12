package com.aucolher.api.favorite;

import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * Favorita sem duplicar: se o par já existe, o banco simplesmente ignora.
     * Resolve até dois cliques simultâneos, que passariam por um "existe? então
     * insere" feito em Java e estourariam a constraint única.
     */
    @Modifying
    @Query(value = """
            INSERT INTO favorites (user_id, animal_id) VALUES (:userId, :animalId)
            ON CONFLICT (user_id, animal_id) DO NOTHING
            """, nativeQuery = true)
    void add(Long userId, Long animalId);

    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.user.id = :userId AND f.animal.id = :animalId")
    void remove(Long userId, Long animalId);

    /** Favoritos com o animal e o dono já carregados, do mais recente para o mais antigo. */
    @EntityGraph(attributePaths = {"animal", "animal.owner"})
    List<Favorite> findByUserIdAndAnimalStatusNotOrderByCreatedAtDescIdDesc(Long userId, AnimalStatus status);

    @Query("""
            SELECT f.animal.id FROM Favorite f
            WHERE f.user.id = :userId AND f.animal.status <> :hiddenStatus
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Long> findAnimalIds(Long userId, AnimalStatus hiddenStatus);
}
