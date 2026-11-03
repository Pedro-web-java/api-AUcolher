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
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Pessoa ou ONG pode anunciar (o admin não), desde que tenha cidade e UF
     * no perfil — é de lá que sai a localização do animal.
     */
    @Transactional
    public AnimalDetailDTO create(String ownerEmail, AnimalRequestDTO dto) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        // O admin modera o site, mas não participa dele
        if (owner.getUserType() == UserType.ADMIN) {
            throw new ForbiddenException("Contas de admin não anunciam animais.");
        }
        requireLocation(owner);

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
        if (animal.isPubliclyVisible()) {
            return AnimalDetailDTO.from(animal);
        }

        // Fora do ar: só o próprio dono vê (removido pela moderação ou de dono
        // banido, nem ele). O admin vê tudo, para moderar.
        boolean ownerSeesOwnInactive = animal.getStatus() == AnimalStatus.INACTIVE
                && animal.isOwnedBy(viewerEmail) && Boolean.TRUE.equals(animal.getOwner().getActive());
        if (ownerSeesOwnInactive || isAdmin(viewerEmail)) {
            return AnimalDetailDTO.from(animal);
        }
        throw animalNotFound();
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

    /** Cidades com animal na vitrine, em ordem alfabética (filtro de cidade da listagem). */
    @Transactional(readOnly = true)
    public List<String> listAvailableCities() {
        return animalRepository.findListedOwnerCities(AnimalStatus.AVAILABLE);
    }

    /** "Meus animais": todos os anúncios da conta logada, inclusive adotados e inativos. */
    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> listMine(String ownerEmail) {
        return summarize(animalRepository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail));
    }

    /**
     * Animais disponíveis de um perfil público. Conta banida não tem perfil
     * público — só o admin ainda vê os animais dela.
     */
    @Transactional(readOnly = true)
    public List<AnimalSummaryDTO> listByOwner(Long userId, String viewerEmail) {
        userRepository.findById(userId)
                .filter(owner -> Boolean.TRUE.equals(owner.getActive()) || isAdmin(viewerEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
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
        Animal animal = findEditable(id, ownerEmail);

        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            throw new BusinessException("Um animal já adotado não pode mais ser editado");
        }

        fill(animal, dto);
        return saveAndRespond(animal);
    }

    /**
     * Muda o status do anúncio: marcar como adotado, tirar do ar (INACTIVE) ou
     * voltar a AVAILABLE. ADOPTED é definitivo. Saindo da vitrine, os pedidos
     * de adoção ativos do animal são cancelados (AnimalLeftListingEvent).
     */
    @Transactional
    public AnimalDetailDTO changeStatus(Long id, String ownerEmail, AnimalStatus newStatus) {
        Animal animal = findEditable(id, ownerEmail);
        if (newStatus == AnimalStatus.REMOVED) {
            throw new BusinessException("Só a moderação remove um anúncio");
        }

        if (animal.getStatus() == newStatus) {
            return AnimalDetailDTO.from(animal);
        }
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            throw new BusinessException("Um animal já adotado não muda mais de status");
        }

        animal.setStatus(newStatus);
        if (newStatus != AnimalStatus.AVAILABLE) {
            eventPublisher.publishEvent(new AnimalLeftListingEvent(animal.getId()));
        }
        return saveAndRespond(animal);
    }

    /**
     * Retorno de uma adoção (uso interno, chamado pelo pedido): o anúncio volta
     * para o doador fora do ar. É a única saída de ADOPTED — o PUT e o PATCH
     * públicos continuam recusando animal adotado. Depois disso o doador edita
     * e reativa o anúncio pelo caminho de sempre.
     */
    @Transactional
    public void returnToOwner(Animal animal) {
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            animal.setStatus(AnimalStatus.INACTIVE);
        }
    }

    // ===================== Repasse (o adotante republica o animal) =====================

    /**
     * O anúncio atual no formato do cadastro (fotos incluídas): o formulário
     * de "Republicar anúncio" já abre preenchido com ele.
     */
    @Transactional(readOnly = true)
    public AnimalRequestDTO toRehomeDraft(Animal animal) {
        return new AnimalRequestDTO(
                animal.getName(), animal.getSpecies(), animal.getBreed(), animal.getSex(),
                animal.getAgeValue(), animal.getAgeUnit(), animal.getSize(),
                animal.getVaccinated(), animal.getNeutered(), animal.getDewormed(), animal.getSpecialNeeds(),
                animal.getEnergyLevel(), animal.getTemperament(), animal.getIndependenceLevel(), animal.getVocalization(),
                animal.getGoodWithChildren(), animal.getGoodWithDogs(), animal.getGoodWithCats(), animal.getApartmentFriendly(),
                animal.getSummary(), animal.getStory(),
                List.copyOf(animal.getPhotos()));
    }

    /**
     * O anúncio novo do repasse: AVAILABLE, em nome do adotante, apontando para
     * o pedido de onde veio. Mesmas regras do cadastro (cidade e UF no perfil).
     */
    @Transactional
    public Animal createRehomed(User adopter, AnimalRequestDTO dto, Long originRequestId) {
        requireLocation(adopter);

        Animal animal = new Animal();
        animal.setOwner(adopter);
        fill(animal, dto);
        animal.setOriginRequestId(originRequestId);
        return animalRepository.saveAndFlush(animal);
    }

    /**
     * "Desisti de doar": o anúncio novo perde o vínculo com a adoção e, se
     * estava no ar, sai como num "tirar do ar" (os pedidos ativos dele são
     * cancelados). A Service do pedido já conferiu que ele não foi adotado.
     */
    @Transactional
    public void withdrawRehomed(Animal animal) {
        animal.setOriginRequestId(null);
        if (animal.getStatus() == AnimalStatus.AVAILABLE) {
            animal.setStatus(AnimalStatus.INACTIVE);
            eventPublisher.publishEvent(new AnimalLeftListingEvent(animal.getId()));
        }
        animalRepository.saveAndFlush(animal);
    }

    /**
     * Moderação: o admin edita o anúncio de outra pessoa. O anúncio continua
     * em nome do dono (que vê "Editado pela moderação"); vale também para o
     * adotado e o fora do ar, que seguem aparecendo para alguém. O removido
     * não muda mais.
     */
    @Transactional
    public AnimalDetailDTO updateByModeration(Long id, AnimalRequestDTO dto, Long adminId) {
        Animal animal = findAnimal(id);
        if (animal.getStatus() == AnimalStatus.REMOVED) {
            throw new BusinessException("Este anúncio foi removido pela moderação e não pode mais ser alterado.");
        }
        fill(animal, dto);
        animal.markModeratedBy(adminId);
        return saveAndRespond(animal);
    }

    /**
     * Moderação: o admin remove o anúncio (REMOVED). Ele sai do site, os
     * pedidos ativos são cancelados (LISTING_REMOVED) e o dono não muda mais
     * nada nele. Não há desfazer. Adotado fica como histórico.
     */
    @Transactional
    public Animal removeByModeration(Long id) {
        Animal animal = animalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anúncio não encontrado."));
        if (animal.getStatus() == AnimalStatus.REMOVED) {
            throw new BusinessException("Este anúncio já foi removido.");
        }
        if (animal.getStatus() == AnimalStatus.ADOPTED) {
            throw new BusinessException("Este animal já foi adotado: o anúncio fica só como histórico.");
        }

        animal.setStatus(AnimalStatus.REMOVED);
        eventPublisher.publishEvent(new AnimalLeftListingEvent(animal.getId(), true));
        return animalRepository.saveAndFlush(animal);
    }

    /**
     * Exclusão da conta: os anúncios no ar saem (INACTIVE); adotados, fora do
     * ar e removidos não mudam. Não publica AnimalLeftListingEvent: quem
     * exclui a conta já cancelou os pedidos ativos com ACCOUNT_DELETED.
     */
    @Transactional
    public void unlistAllOf(Long ownerId) {
        animalRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, AnimalStatus.AVAILABLE)
                .forEach(animal -> animal.setStatus(AnimalStatus.INACTIVE));
    }

    /** "Excluir" o anúncio: exclusão lógica, o animal vira INACTIVE. */
    @Transactional
    public void deactivate(Long id, String ownerEmail) {
        changeStatus(id, ownerEmail, AnimalStatus.INACTIVE);
    }

    /** A localização do animal sai do perfil de quem anuncia. */
    private static void requireLocation(User owner) {
        if (owner.getCity() == null || owner.getState() == null) {
            throw new BusinessException("Complete a cidade e a UF do seu perfil antes de anunciar um animal");
        }
    }

    /** Animal que o dono quer alterar: além de ser dele, não pode ter sido removido pela moderação. */
    private Animal findEditable(Long id, String ownerEmail) {
        Animal animal = findOwned(id, ownerEmail);
        if (animal.getStatus() == AnimalStatus.REMOVED) {
            throw new ForbiddenException("Este anúncio foi removido pela moderação e não pode mais ser alterado.");
        }
        return animal;
    }

    private boolean isAdmin(String email) {
        return email != null && userRepository.findByEmail(email)
                .map(user -> user.getUserType() == UserType.ADMIN)
                .orElse(false);
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
