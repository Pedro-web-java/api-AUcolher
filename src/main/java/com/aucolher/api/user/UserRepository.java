package com.aucolher.api.user;

import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCnpj(String cnpj);

    /** Painel do admin: as contas de pessoas e ONGs (sem outros admins), das mais recentes para as mais antigas. */
    List<User> findByUserTypeNotOrderByCreatedAtDescIdDesc(UserType excluded);
}
