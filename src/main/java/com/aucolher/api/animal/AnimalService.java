package com.aucolher.api.animal;

import com.aucolher.api.animal.dto.AnimalDetailDTO;
import com.aucolher.api.animal.dto.AnimalFilterDTO;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.shared.dto.PageDTO;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regras dos anúncios de animais. Como nas outras Services, o DTO chega já
 * validado e normalizado; aqui ficam só as regras de negócio.
 */
@Service
@RequiredArgsConstructor
public class AnimalService {

    static final int MAX_PAGE_SIZE = 50;

    private final AnimalRepository animalRepository;
    private final UserRepository userRepository;

    /**
     * Qualquer conta (ONG ou usuário comum) pode anunciar, desde que tenha
     * cidade e UF no perfil — é de lá que sai a localização do animal.
     */
    @Transactional
    public AnimalDetailDTO create(String ownerEmail, AnimalRequestDTO dto) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        if (owner.getCity() == null || owner.getState() == null) {
            throw new BusinessException("Complete a cidade e a UF do seu perfil antes de anunciar um animal");
        }

        Animal animal = new Animal();
        animal.setOwner(owner);
        fill(animal, dto);

        return AnimalDetailDTO.from(animalRepository.save(animal));
    }

    /**
     * Detalhes públicos do animal. Anúncio inativo só aparece para o próprio
     * dono; para os outros ele "não existe" (404), como se tivesse sido apagado.
     *
     * @param emailVisitante e-mail de quem está vendo, ou null se não estiver logado
     */
    @Transactional(readOnly = true)
    public AnimalDetailDTO getDetail(Long id, String viewerEmail) {
        Animal animal = findAnimal(id);

        if (animal.getStatus() == AnimalStatus.INACTIVE && !animal.isOwnedBy(viewerEmail)) {
            throw animalNotFound();
        }

        return AnimalDetailDTO.from(animal);
    }

    /** Vitrine pública: só disponíveis, com filtros, do anúncio mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public PageDTO<AnimalSummaryDTO> listAvailable(AnimalFilterDTO filter, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Animal> result = animalRepository.findAll(AnimalSpecifications.available(filter), pageRequest);
        Map<Long, AnimalSummaryDTO> summaries = summariesById(result.getContent());
        return PageDTO.from(result.map(animal -> summaries.get(animal.getId())));
    }

    /** "Meus animais": todos os anúncios da conta logada, inclusive adotados e inativos. */
    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> listMine(String ownerEmail) {
        return summarize(animalRepository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail));
    }

    /** Animais disponíveis de um perfil público. */
    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> listByOwner(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        return summarize(animalRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(userId, AnimalStatus.AVAILABLE));
    }

    /**
     * Monta os cards de uma lista de animais, buscando as fotos de capa numa
     * consulta só. Público porque os favoritos usam os mesmos cards.
     */
    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> summarize(List<Animal> animals) {
        Map<Long, AnimalSummaryDTO> summaries = summariesById(animals);
        return animals.stream().map(animal -> summaries.get(animal.getId())).toList();
    }

    private Map<Long, AnimalSummaryDTO> summariesById(List<Animal> animals) {
        if (animals.isEmpty()) return Map.of();

        Map<Long, String> covers = animalRepository.findCoverPhotos(animals.stream().map(Animal::getId).toList()).stream()
                .collect(Collectors.toMap(AnimalRepository.CoverPhoto::getAnimalId, AnimalRepository.CoverPhoto::getUrl));

        return animals.stream().collect(Collectors.toMap(
                Animal::getId,
                animal -> AnimalSummaryDTO.from(animal, covers.get(animal.getId()))));
    }

    /**
     * Substitui o anúncio inteiro (PUT). Animal adotado fica congelado; um
     * inativo pode ser editado antes de voltar ao ar.
     */
    @Transactional
    public AnimalDetailDTO update(Long id, String ownerEmail, AnimalRequestDTO dto) {
        Animal animal = findOwned(id, ownerEmail);

        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            throw new BusinessException("Um animal já adotado não pode mais ser editado");
        }

        fill(animal, dto);
        return saveAndRespond(animal);
    }

    /**
     * Muda o status do anúncio: marcar como adotado, tirar do ar (INACTIVE) ou
     * voltar a AVAILABLE. ADOPTED é definitivo.
     */
    @Transactional
    public AnimalDetailDTO changeStatus(Long id, String ownerEmail, AnimalStatus newStatus) {
        Animal animal = findOwned(id, ownerEmail);

        if (animal.getStatus() == newStatus) {
            return AnimalDetailDTO.from(animal);
        }
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            throw new BusinessException("Um animal já adotado não muda mais de status");
        }

        animal.setStatus(newStatus);
        return saveAndRespond(animal);
    }

    /** "Excluir" o anúncio: exclusão lógica, o animal vira INACTIVE. */
    @Transactional
    public void deactivate(Long id, String ownerEmail) {
        changeStatus(id, ownerEmail, AnimalStatus.INACTIVE);
    }

    /** Animal que o usuário logado quer alterar: precisa existir e ser dele. */
    private Animal findOwned(Long id, String ownerEmail) {
        Animal animal = findAnimal(id);
        if (!animal.isOwnedBy(ownerEmail)) {
            throw new ForbiddenException("Só quem anunciou pode alterar este animal");
        }
        return animal;
    }

    /**
     * O flush força o UPDATE agora — é nele que rodam o @PreUpdate (faixa
     * etária e data de atualização) — para a resposta já sair com os valores novos.
     */
    private AnimalDetailDTO saveAndRespond(Animal animal) {
        return AnimalDetailDTO.from(animalRepository.saveAndFlush(animal));
    }

    private Animal findAnimal(Long id) {
        return animalRepository.findById(id).orElseThrow(this::animalNotFound);
    }

    private ResourceNotFoundException animalNotFound() {
        return new ResourceNotFoundException("Animal não encontrado");
    }

    /** Copia o anúncio do DTO para a entidade — usado no cadastro e na edição. */
    private void fill(Animal animal, AnimalRequestDTO dto) {
        animal.setName(dto.name());
        animal.setSpecies(dto.species());
        animal.setBreed(dto.breed());
        animal.setSex(dto.sex());
        animal.setAgeValue(dto.ageValue());
        animal.setAgeUnit(dto.ageUnit());
        animal.setSize(dto.size());
        animal.setVaccinated(dto.vaccinated());
        animal.setNeutered(dto.neutered());
        animal.setDewormed(dto.dewormed());
        animal.setSpecialNeeds(dto.specialNeeds());
        animal.setEnergyLevel(dto.energyLevel());
        animal.setTemperament(dto.temperament());
        animal.setIndependenceLevel(dto.independenceLevel());
        animal.setVocalization(dto.vocalization());
        animal.setGoodWithChildren(dto.goodWithChildren());
        animal.setGoodWithDogs(dto.goodWithDogs());
        animal.setGoodWithCats(dto.goodWithCats());
        animal.setApartmentFriendly(dto.apartmentFriendly());
        animal.setSummary(dto.summary());
        animal.setStory(dto.story());

        // A ordem enviada é a ordem de exibição; a primeira foto é a capa
        animal.getPhotos().clear();
        animal.getPhotos().addAll(dto.photos());
    }
}
