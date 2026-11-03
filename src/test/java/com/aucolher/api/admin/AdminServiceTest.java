package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminAnimalDTO;
import com.aucolher.api.admin.dto.AdminUserDTO;
import com.aucolher.api.admin.dto.ListingStatus;
import com.aucolher.api.adoption.AdoptionRequestRepository;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** As listas do painel do admin. */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalService animalService;
    @Mock
    private AdoptionRequestRepository requestRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void usersComeWithHowManyListingsTheyHave() {
        User ngo = User.builder().id(2L).name("Apacri").userType(UserType.NGO).verified(true).build();
        User person = User.builder().id(3L).name("Maria").userType(UserType.PERSON).build();
        when(userRepository.findByUserTypeNotOrderByCreatedAtDescIdDesc(UserType.ADMIN)).thenReturn(List.of(ngo, person));
        when(animalRepository.countByOwner()).thenReturn(List.of(ownerCount(2L, 4L)));

        List<AdminUserDTO> users = adminService.listUsers();

        assertThat(users).extracting(AdminUserDTO::animalsCount).containsExactly(4L, 0L);
        assertThat(users.get(0).verified()).isTrue();
        assertThat(users.get(1).active()).isTrue();
    }

    @Test
    void availableAnimalWithAnAcceptedRequestIsInProgress() {
        Animal available = animal(1L, AnimalStatus.AVAILABLE);
        Animal inProgress = animal(2L, AnimalStatus.AVAILABLE);
        Animal removed = animal(3L, AnimalStatus.REMOVED);
        List<Animal> animals = List.of(available, inProgress, removed);
        when(animalRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(animals);
        when(requestRepository.findAnimalIdsWithStatus(AdminService.IN_PROGRESS)).thenReturn(List.of(2L));
        when(animalService.summarize(anyList())).thenReturn(animals.stream().map(a -> AnimalSummaryDTO.from(a, null)).toList());

        assertThat(adminService.listAnimals()).extracting(AdminAnimalDTO::listingStatus)
                .containsExactly(ListingStatus.AVAILABLE, ListingStatus.IN_PROGRESS, ListingStatus.REMOVED);
    }

    private static Animal animal(Long id, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(id);
        animal.setStatus(status);
        animal.setOwner(User.builder().id(2L).name("Apacri").userType(UserType.NGO).build());
        return animal;
    }

    private static AnimalRepository.OwnerCount ownerCount(Long ownerId, Long total) {
        return new AnimalRepository.OwnerCount() {
            public Long getOwnerId() { return ownerId; }
            public Long getTotal() { return total; }
        };
    }
}
