package com.aucolher.api.favorite;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.favorite.entity.Favorite;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Favoritos do usuário logado. Animais inativos (tirados do ar pelo dono)
 * somem da lista; os adotados continuam, com o status, para o frontend
 * poder mostrar "adotado".
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    /** Fora do ar pelo dono ou removido pela moderação: some da lista de favoritos. */
    private static final Set<AnimalStatus> HIDDEN = EnumSet.of(AnimalStatus.INACTIVE, AnimalStatus.REMOVED);

    private final FavoriteRepository favoriteRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> list(String email) {
        List<Animal> animals = favoriteRepository
                .findByUserIdAndAnimalStatusNotInAndAnimalOwnerActiveTrueOrderByCreatedAtDescIdDesc(findUser(email).getId(), HIDDEN)
                .stream()
                .map(Favorite::getAnimal)
                .toList();
        return animalService.summarize(animals);
    }

    /** Só os ids, para o frontend marcar os corações nos cards. */
    @Transactional(readOnly = true)
    public List<Long> listIds(String email) {
        return favoriteRepository.findAnimalIds(findUser(email).getId(), HIDDEN);
    }

    /** Idempotente: favoritar de novo não duplica nem dá erro. */
    @Transactional
    public void add(String email, Long animalId) {
        User user = findUser(email);
        // O admin modera o site, mas não participa dele
        if (user.getUserType() == UserType.ADMIN) {
            throw new ForbiddenException("Contas de admin não favoritam animais.");
        }
        Animal animal = animalRepository.findById(animalId)
                .filter(Animal::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Animal não encontrado"));

        favoriteRepository.add(user.getId(), animal.getId());
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
