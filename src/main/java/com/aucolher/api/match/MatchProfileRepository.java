package com.aucolher.api.match;

import com.aucolher.api.match.entity.MatchProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Perfis AUmatch — a chave é o id do usuário. */
public interface MatchProfileRepository extends JpaRepository<MatchProfile, Long> {
}
