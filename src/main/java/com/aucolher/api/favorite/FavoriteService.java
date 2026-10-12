package com.aucolher.api.favorite;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.favorite.entity.Favorite;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Favoritos do usuário logado. Animais inativos (tirados do ar pelo dono)
 * somem da lista; os adotados continuam, com o status, para o frontend
 * poder mostrar "adotado".
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> list(String email) {
        List<Animal> animals = favoriteRepository
                .findByUserIdAndAnimalStatusNotOrderByCreatedAtDescIdDesc(findUser(email).getId(), AnimalStatus.INACTIVE)
                .stream()
                .map(Favorite::getAnimal)
                .toList();
        return animalService.summarize(animals);
    }

    /** Só os ids, para o frontend marcar os corações nos cards. */
    @Transactional(readOnly = true)
    public List<Long> listIds(String email) {
        return favoriteRepository.findAnimalIds(findUser(email).getId(), AnimalStatus.INACTIVE);
    }

    /** Idempotente: favoritar de novo não duplica nem dá erro. */
    @Transactional
    public void add(String email, Long animalId) {
        Animal animal = animalRepository.findById(animalId)
                .filter(found -> found.getStatus() != AnimalStatus.INACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Animal não encontrado"));

        favoriteRepository.add(findUser(email).getId(), animal.getId());
    }

    /** Idempotente: desfavoritar o que não está favoritado não dá erro. */
    @Transactional
    public void remove(String email, Long animalId) {
        favoriteRepository.remove(findUser(email).getId(), animalId);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
    }
}
