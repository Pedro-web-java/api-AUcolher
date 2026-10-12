package com.aucolher.api.animal;

import com.aucolher.api.animal.dto.AnimalFilterDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.user.entity.User;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Monta o WHERE da listagem pública a partir dos filtros recebidos. Cada
 * filtro só entra na consulta se veio na requisição, e todos se somam (AND):
 * ?species=CAT&sizes=SMALL vira "species IN ('CAT') AND size IN ('SMALL')".
 */
final class AnimalSpecifications {

    private AnimalSpecifications() {}

    static Specification<Animal> available(AnimalFilterDTO filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();

            // A vitrine só mostra quem está disponível — adotados e inativos nunca aparecem
            conditions.add(cb.equal(root.get("status"), AnimalStatus.AVAILABLE));

            addIn(conditions, root.get("species"), filter.species());
            addIn(conditions, root.get("size"), filter.sizes());
            addIn(conditions, root.get("sex"), filter.sexes());
            addIn(conditions, root.get("ageGroup"), filter.ageGroups());
            addIn(conditions, root.get("energyLevel"), filter.energyLevels());
            addIn(conditions, root.get("temperament"), filter.temperaments());

            if (Boolean.TRUE.equals(filter.specialNeeds())) {
                conditions.add(cb.isTrue(root.get("specialNeeds")));
            }

            // Cidade e UF são as do dono (o animal está onde o dono está)
            Join<Animal, User> owner = root.join("owner");

            if (filter.city() != null) {
                conditions.add(cb.equal(cb.lower(owner.get("city")), filter.city().toLowerCase(Locale.ROOT)));
            }
            if (filter.state() != null) {
                conditions.add(cb.equal(owner.get("state"), filter.state()));
            }

            if (filter.search() != null) {
                String term = "%" + escapeLike(filter.search().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("name")), term, '\\'),
                        cb.like(cb.lower(root.get("breed")), term, '\\'),
                        cb.like(cb.lower(owner.get("city")), term, '\\')
                ));
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static void addIn(List<Predicate> conditions, Expression<?> field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            conditions.add(field.in(values));
        }
    }

    /** % e _ digitados na busca são texto, não curingas do LIKE. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
