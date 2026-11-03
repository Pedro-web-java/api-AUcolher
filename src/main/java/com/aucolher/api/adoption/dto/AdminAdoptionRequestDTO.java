package com.aucolher.api.adoption.dto;

import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.animal.entity.Species;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;

import java.time.LocalDateTime;

/**
 * Um pedido na versão enxuta do painel do admin: só o necessário para o
 * funil de adoções, as adoções por mês e o ranking de ONGs.
 */
public record AdminAdoptionRequestDTO(
        Long id,
        AdoptionStatus status,
        CancelReason cancelReason,
        LocalDateTime createdAt,
        LocalDateTime concludedAt,
        LocalDateTime closedAt,
        AnimalRef animal,
        AccountRef owner,
        AccountRef adopter
) {

    public record AnimalRef(Long id, String name, Species species, AnimalStatus status, String coverPhoto) {
    }

    public record AccountRef(Long id, String name, UserType userType) {

        static AccountRef from(User user) {
            return new AccountRef(user.getId(), user.getName(), user.getUserType());
        }
    }

    public static AdminAdoptionRequestDTO from(AdoptionRequest request, String coverPhoto) {
        Animal animal = request.getAnimal();
        return new AdminAdoptionRequestDTO(
                request.getId(),
                request.getStatus(),
                request.getCancelReason(),
                request.getCreatedAt(),
                request.getConcludedAt(),
                request.getClosedAt(),
                new AnimalRef(animal.getId(), animal.getName(), animal.getSpecies(), animal.getStatus(), coverPhoto),
                AccountRef.from(animal.getOwner()),
                AccountRef.from(request.getAdopter()));
    }
}
