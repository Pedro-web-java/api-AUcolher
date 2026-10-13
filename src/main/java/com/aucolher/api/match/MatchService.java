package com.aucolher.api.match;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.match.dto.MatchCardDTO;
import com.aucolher.api.match.dto.MatchProfileDTO;
import com.aucolher.api.match.dto.MatchProfileRequestDTO;
import com.aucolher.api.match.entity.MatchProfile;
import com.aucolher.api.match.entity.SpeciesPreference;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AUmatch da pessoa logada: as respostas do quiz e o deck ordenado por
 * compatibilidade. Só pessoas físicas chegam aqui — a SecurityConfig barra
 * ONG e admin em /api/match.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    /** Desempate do deck: score maior primeiro; no empate, o anúncio mais recente. */
    private static final Comparator<Animal> NEWEST_FIRST =
            Comparator.comparing(Animal::getCreatedAt).thenComparing(Animal::getId).reversed();

    private final MatchProfileRepository matchProfileRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MatchProfileDTO getProfile(String email) {
        return matchProfileRepository.findById(findUser(email).getId())
                .map(MatchProfileDTO::from)
                .orElseGet(MatchProfileDTO::empty);
    }

    /** Cria o perfil na primeira resposta; depois, substitui as respostas inteiras. */
    @Transactional
    public MatchProfileDTO saveProfile(String email, MatchProfileRequestDTO request) {
        Long userId = findUser(email).getId();
        MatchProfile profile = matchProfileRepository.findById(userId).orElseGet(() -> new MatchProfile(userId));
        fill(profile, request);
        // saveAndFlush: o @PreUpdate roda antes de a resposta ser montada
        return MatchProfileDTO.from(matchProfileRepository.saveAndFlush(profile));
    }

    /**
     * O deck inteiro, do maior score para o menor: animais disponíveis, de
     * contas ativas, da espécie procurada e que não sejam da própria pessoa.
     * Quem ainda não fez o quiz recebe o deck com o score de base.
     *
     * Volta tudo de uma vez, sem paginação — o score depende de cada animal,
     * então ordenar no banco não é possível, e no tamanho do projeto a lista
     * cabe numa resposta só.
     */
    @Transactional(readOnly = true)
    public List<MatchCardDTO> deck(String email) {
        User user = findUser(email);
        MatchProfile profile = matchProfileRepository.findById(user.getId()).orElse(null);

        List<Animal> candidates = animalRepository
                .findByStatusAndOwnerActiveTrueAndOwnerIdNot(AnimalStatus.AVAILABLE, user.getId()).stream()
                .filter(animal -> isWantedSpecies(profile, animal))
                .toList();

        Map<Long, MatchResult> results = candidates.stream()
                .collect(Collectors.toMap(Animal::getId, animal -> MatchScoreCalculator.score(profile, animal)));

        List<Animal> ranked = candidates.stream()
                .sorted(Comparator.<Animal>comparingInt(animal -> results.get(animal.getId()).score()).reversed()
                        .thenComparing(NEWEST_FIRST))
                .toList();

        // summarize mantém a ordem e busca as capas numa consulta só
        List<AnimalSummaryDTO> cards = animalService.summarize(ranked);
        return IntStream.range(0, ranked.size())
                .mapToObj(index -> MatchCardDTO.of(cards.get(index), results.get(ranked.get(index).getId())))
                .toList();
    }

    /** "Ambos" ou sem resposta: todas as espécies, inclusive OTHER. */
    private static boolean isWantedSpecies(MatchProfile profile, Animal animal) {
        SpeciesPreference preference = profile == null ? null : profile.getSpeciesPreference();
        if (preference == null || preference == SpeciesPreference.BOTH) return true;
        return animal.getSpecies().name().equals(preference.name());
    }

    private void fill(MatchProfile profile, MatchProfileRequestDTO request) {
        profile.setHousing(request.housing());
        profile.setExerciseRoutine(request.exerciseRoutine());
        profile.setTimeAway(request.timeAway());
        profile.setHasChildren(request.hasChildren());
        profile.setHasDogs(request.hasDogs());
        profile.setHasCats(request.hasCats());
        profile.setSpeciesPreference(request.speciesPreference());
        profile.setIdealPetProfile(request.idealPetProfile());
        profile.setPreferredSize(request.preferredSize());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
    }
}
