package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminAnimalDTO;
import com.aucolher.api.admin.dto.AdminUserDTO;
import com.aucolher.api.admin.dto.ListingStatus;
import com.aucolher.api.adoption.AdoptionRequestRepository;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * As listas do painel do admin: a plataforma inteira, sem paginação (o
 * volume do TCC é pequeno — a busca e os filtros ficam no cliente).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    /** Pedido que deixa um animal disponível "em andamento". */
    static final Set<AdoptionStatus> IN_PROGRESS = EnumSet.of(AdoptionStatus.ACCEPTED, AdoptionStatus.AWAITING_DELIVERY);

    private final UserRepository userRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final AdoptionRequestRepository requestRepository;

    /** Contas de pessoas e ONGs, mais recentes primeiro, com quantos anúncios cada uma tem. */
    @Transactional(readOnly = true)
    public List<AdminUserDTO> listUsers() {
        Map<Long, Long> animalsCount = animalRepository.countByOwner().stream()
                .collect(Collectors.toMap(AnimalRepository.OwnerCount::getOwnerId, AnimalRepository.OwnerCount::getTotal));
        return userRepository.findByUserTypeNotOrderByCreatedAtDescIdDesc(UserType.ADMIN).stream()
                .map(user -> AdminUserDTO.from(user, animalsCount.getOrDefault(user.getId(), 0L)))
                .toList();
    }

    /** Todos os anúncios, em qualquer status, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<AdminAnimalDTO> listAnimals() {
        List<Animal> animals = animalRepository.findAllByOrderByCreatedAtDescIdDesc();
        Set<Long> inProgress = new HashSet<>(requestRepository.findAnimalIdsWithStatus(IN_PROGRESS));
        List<AnimalSummaryDTO> cards = animalService.summarize(animals);

        return cards.stream()
                .map(card -> new AdminAnimalDTO(card, listingStatus(card.status(), inProgress.contains(card.id()))))
                .toList();
    }

    static ListingStatus listingStatus(AnimalStatus status, boolean hasRequestInProgress) {
        return switch (status) {
            case AVAILABLE -> hasRequestInProgress ? ListingStatus.IN_PROGRESS : ListingStatus.AVAILABLE;
            case ADOPTED -> ListingStatus.ADOPTED;
            case INACTIVE -> ListingStatus.INACTIVE;
            case REMOVED -> ListingStatus.REMOVED;
        };
    }
}
