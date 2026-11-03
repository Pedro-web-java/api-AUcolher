package com.aucolher.api.adoption;

import com.aucolher.api.adoption.dto.AdminAdoptionRequestDTO;
import com.aucolher.api.adoption.dto.AdopterDTO;
import com.aucolher.api.adoption.dto.AdoptionRequestDTO;
import com.aucolher.api.adoption.dto.RegisterReturnDTO;
import com.aucolher.api.adoption.dto.RehomeDTO;
import com.aucolher.api.adoption.dto.RequestReviewsDTO;
import com.aucolher.api.adoption.dto.ReviewDTO;
import com.aucolher.api.adoption.entity.AdoptionReview;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.adoption.entity.ReviewRole;
import com.aucolher.api.animal.AnimalLeftListingEvent;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.match.MatchProfileRepository;
import com.aucolher.api.match.MatchScoreCalculator;
import com.aucolher.api.match.entity.MatchProfile;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pedidos de adoção. Quem pede é pessoa ou ONG (a SecurityConfig barra o
 * admin); o dono é sempre o dono do animal. Só as duas partes veem um pedido.
 */
@Service
@RequiredArgsConstructor
public class AdoptionRequestService {

    private final AdoptionRequestRepository requestRepository;
    private final AdoptionReviewRepository reviewRepository;
    private final AnimalRepository animalRepository;
    private final AnimalService animalService;
    private final MatchProfileRepository matchProfileRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    /**
     * Novo pedido (PENDING). O animal precisa estar disponível e não ser de
     * quem pede, e não pode haver outro pedido não cancelado da mesma pessoa
     * para ele: recusado é definitivo, só um cancelado libera um pedido novo.
     */
    @Transactional
    public AdoptionRequestDTO create(String email, Long animalId) {
        User adopter = findUser(email);
        Animal animal = animalRepository.findById(animalId)
                .filter(Animal::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Animal não encontrado"));

        if (animal.isOwnedBy(email)) {
            throw new ForbiddenException("Você não pode pedir para adotar o seu próprio animal");
        }
        if (animal.getStatus() != AnimalStatus.AVAILABLE) {
            throw new BusinessException("Este animal já foi adotado");
        }
        requestRepository.findFirstByAdopterIdAndAnimalIdAndStatusNot(adopter.getId(), animalId, AdoptionStatus.CANCELLED)
                .ifPresent(existing -> {
                    throw new BusinessException(existing.getStatus() == AdoptionStatus.REJECTED
                            ? "O dono já recusou o seu pedido para este animal"
                            : "Você já enviou um pedido para este animal");
                });

        try {
            return toDtos(List.of(requestRepository.saveAndFlush(new AdoptionRequest(animal, adopter)))).get(0);
        } catch (DataIntegrityViolationException e) {
            // Dois cliques ao mesmo tempo: o índice único do banco segura o segundo
            throw new BusinessException("Você já enviou um pedido para este animal");
        }
    }

    /** "Meus interesses": os pedidos que a pessoa enviou. */
    @Transactional(readOnly = true)
    public List<AdoptionRequestDTO> listSent(String email) {
        return toDtos(requestRepository.findByAdopterIdOrderByCreatedAtDescIdDesc(findUser(email).getId()));
    }

    /** Os pedidos dos animais da conta, em qualquer status. */
    @Transactional(readOnly = true)
    public List<AdoptionRequestDTO> listReceived(String email) {
        return toDtos(requestRepository.findByAnimalOwnerIdOrderByCreatedAtDescIdDesc(findUser(email).getId()));
    }

    /** Painel do admin: todos os pedidos, enxutos, com a foto de capa de cada animal numa consulta só. */
    @Transactional(readOnly = true)
    public List<AdminAdoptionRequestDTO> listAllForAdmin() {
        List<AdoptionRequest> requests = requestRepository.findAllByOrderByCreatedAtDescIdDesc();
        if (requests.isEmpty()) return List.of();

        List<Long> animalIds = requests.stream().map(request -> request.getAnimal().getId()).distinct().toList();
        Map<Long, String> covers = animalRepository.findCoverPhotos(animalIds).stream()
                .collect(Collectors.toMap(AnimalRepository.CoverPhoto::getAnimalId, AnimalRepository.CoverPhoto::getUrl));

        return requests.stream()
                .map(request -> AdminAdoptionRequestDTO.from(request, covers.get(request.getAnimal().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdoptionRequestDTO get(Long id, String email) {
        AdoptionRequest request = findRequest(id);
        if (!request.isAdopter(email) && !request.isOwnedBy(email)) {
            throw new ForbiddenException("Só quem participa do pedido pode vê-lo");
        }
        return toDtos(List.of(request)).get(0);
    }

    /**
     * Muda o status de um pedido. Cada transição tem dono certo e status de
     * partida certo — papel errado responde 403, status errado responde 400:
     * - dono: PENDING → ACCEPTED ou REJECTED; ACCEPTED → AWAITING_DELIVERY;
     *   ACCEPTED/AWAITING_DELIVERY → CANCELLED (desistiu);
     * - adotante: AWAITING_DELIVERY → CONCLUDED; AWAITING_DELIVERY → ACCEPTED ("ainda não
     *   recebi"); qualquer ativo → CANCELLED (desistiu).
     * Repetir o status atual não muda nada (clique duplo).
     */
    @Transactional
    public AdoptionRequestDTO changeStatus(Long id, String email, AdoptionStatus target) {
        AdoptionRequest request = findRequest(id);
        boolean isOwner = request.isOwnedBy(email);
        boolean isAdopter = request.isAdopter(email);
        if (!isOwner && !isAdopter) {
            throw new ForbiddenException("Só quem participa do pedido pode alterá-lo");
        }
        if (request.getStatus() == target) {
            return toDtos(List.of(request)).get(0);
        }

        switch (target) {
            case ACCEPTED -> {
                if (isAdopter && request.getStatus() == AdoptionStatus.AWAITING_DELIVERY) {
                    // "Ainda não recebi": o dono confirmou a entrega, mas o pet não
                    // chegou (engano ou golpe). O pedido volta a aceito e o chat reabre
                    request.returnToAccepted();
                } else {
                    requireRole(isOwner, "Só o dono do animal pode aceitar o pedido");
                    requireStatus(request, "Só um pedido pendente pode ser aceito", AdoptionStatus.PENDING);
                    request.accept();
                }
            }
            case REJECTED -> {
                requireRole(isOwner, "Só o dono do animal pode recusar o pedido");
                requireStatus(request, "Só um pedido pendente pode ser recusado", AdoptionStatus.PENDING);
                request.reject();
            }
            case AWAITING_DELIVERY -> {
                requireRole(isOwner, "Só o dono do animal confirma a entrega");
                requireStatus(request, "Só um pedido aceito pode ir para a entrega", AdoptionStatus.ACCEPTED);
                request.requestDelivery();
            }
            case CONCLUDED -> {
                requireRole(isAdopter, "Só quem adotou confirma a chegada do pet");
                requireStatus(request, "A chegada só é confirmada depois que o dono confirmar a entrega",
                        AdoptionStatus.AWAITING_DELIVERY);
                conclude(request);
            }
            case CANCELLED -> {
                if (isAdopter) {
                    requireStatus(request, "Este pedido já foi encerrado", AdoptionStatus.PENDING,
                            AdoptionStatus.ACCEPTED, AdoptionStatus.AWAITING_DELIVERY);
                    request.cancel(CancelReason.ADOPTER_WITHDREW);
                } else {
                    // Pendente o dono não cancela: recusa
                    requireStatus(request, "Para um pedido pendente, use Recusar",
                            AdoptionStatus.ACCEPTED, AdoptionStatus.AWAITING_DELIVERY);
                    request.cancel(CancelReason.OWNER_WITHDREW);
                }
            }
            case PENDING -> throw new BusinessException("Um pedido não volta a ficar pendente");
            case RETURNED, REHOMED -> throw new BusinessException("Retorno e repasse têm ações próprias");
        }

        return toDtos(List.of(requestRepository.saveAndFlush(request))).get(0);
    }

    /**
     * Fecha a adoção numa transação só: o pedido é concluído (com o score do
     * AUmatch congelado), o animal vira ADOPTED e os outros pedidos ativos dele
     * são cancelados (ANIMAL_ADOPTED).
     * A linha do animal fica travada (FOR UPDATE) até o fim — se dois
     * adotantes confirmarem ao mesmo tempo, o segundo encontra o animal já
     * adotado e o próprio pedido já cancelado.
     */
    private void conclude(AdoptionRequest request) {
        Animal animal = request.getAnimal();
        entityManager.refresh(animal, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(request);
        if (animal.getStatus() != AnimalStatus.AVAILABLE || request.getStatus() != AdoptionStatus.AWAITING_DELIVERY) {
            throw new BusinessException("Este animal já foi adotado");
        }

        request.conclude(frozenScore(request));
        requestRepository.findByAnimalIdAndStatusIn(animal.getId(), AdoptionStatus.ACTIVE).stream()
                .filter(other -> !other.getId().equals(request.getId()))
                .forEach(other -> other.cancel(CancelReason.ANIMAL_ADOPTED));
        animal.setStatus(AnimalStatus.ADOPTED);
    }

    /**
     * O score que mede a taxa de reabandono por faixa: o do momento da
     * conclusão, que não muda mais. Só existe para pessoa com o quiz completo;
     * ONG adotando ou quiz pela metade ficam sem (null).
     */
    private Integer frozenScore(AdoptionRequest request) {
        User adopter = request.getAdopter();
        if (adopter.getUserType() == UserType.NGO) return null;
        return matchProfileRepository.findById(adopter.getId())
                .filter(MatchProfile::isComplete)
                .map(profile -> MatchScoreCalculator.score(profile, request.getAnimal()).score())
                .orElse(null);
    }

    // ===================== Retorno e repasse (taxa de reabandono) =====================

    /**
     * "Animal retornado": quem doou registra que o animal voltou. O pedido vira
     * RETURNED e o anúncio volta para o doador fora do ar (INACTIVE). Repetir
     * num pedido já RETURNED corrige a data, o motivo e a nota, sem mexer no animal.
     * Checagens na ordem do contrato: papel, adotante ONG, status e corpo.
     */
    @Transactional
    public AdoptionRequestDTO registerReturn(Long id, String email, RegisterReturnDTO body) {
        AdoptionRequest request = findRequest(id);
        requireRole(request.isOwnedBy(email), "Só quem doou o animal registra o retorno");
        requireNotNgoAdoption(request);
        lock(request);
        if (request.getStatus() == AdoptionStatus.REHOMED) {
            throw new BusinessException("O adotante já anunciou este animal para uma nova adoção");
        }
        requireStatus(request, "Só uma adoção concluída pode ter o retorno registrado",
                AdoptionStatus.CONCLUDED, AdoptionStatus.RETURNED);
        requireReason(body.reason(), body.note());
        requireReturnDate(request, body.returnDate());

        if (request.getStatus() == AdoptionStatus.CONCLUDED) {
            animalService.returnToOwner(request.getAnimal());
        }
        request.recordReturn(body.returnDate(), body.reason(), body.note());
        return toDtos(List.of(requestRepository.saveAndFlush(request))).get(0);
    }

    /** "Republicar anúncio": o anúncio original no formato do cadastro, para o formulário abrir preenchido. */
    @Transactional(readOnly = true)
    public AnimalRequestDTO getRehomeDraft(Long id, String email) {
        AdoptionRequest request = findRequest(id);
        requireRole(request.isAdopter(email), "Só quem adotou pode republicar o animal");
        requireNotNgoAdoption(request);
        requireRehomable(request);
        return animalService.toRehomeDraft(request.getAnimal());
    }

    /**
     * O adotante anuncia o animal para uma nova adoção, numa transação só:
     * nasce o anúncio novo (AVAILABLE, em nome dele, apontando para este
     * pedido) e o pedido vira REHOMED. O anúncio original continua ADOPTED
     * no histórico de quem doou.
     */
    @Transactional
    public AdoptionRequestDTO rehome(Long id, String email, RehomeDTO body) {
        AdoptionRequest request = findRequest(id);
        requireRole(request.isAdopter(email), "Só quem adotou pode republicar o animal");
        requireNotNgoAdoption(request);
        lock(request);
        requireRehomable(request);
        requireReason(body.reason(), body.note());

        animalService.createRehomed(request.getAdopter(), body.animal(), request.getId());
        request.rehome(body.reason(), body.note());
        return toDtos(List.of(requestRepository.saveAndFlush(request))).get(0);
    }

    /**
     * "Desisti de doar": enquanto o anúncio novo não gerou uma adoção, o
     * repasse é desfeito — o anúncio sai do ar e perde o vínculo, e o pedido
     * volta a CONCLUDED sem os dados do repasse. O anúncio fica travado para
     * não concluir uma adoção no meio do caminho.
     */
    @Transactional
    public AdoptionRequestDTO undoRehome(Long id, String email) {
        AdoptionRequest request = findRequest(id);
        requireRole(request.isAdopter(email), "Só quem adotou pode republicar o animal");
        requireNotNgoAdoption(request);
        lock(request);
        requireStatus(request, "Esta adoção não foi republicada", AdoptionStatus.REHOMED);

        animalRepository.findByOriginRequestId(request.getId()).ifPresent(listing -> {
            entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE);
            if (listing.getStatus() == AnimalStatus.ADOPTED) {
                throw new BusinessException("O anúncio já gerou uma nova adoção: não dá para desfazer");
            }
            animalService.withdrawRehomed(listing);
        });
        request.undoRehome();
        return toDtos(List.of(requestRepository.saveAndFlush(request))).get(0);
    }

    private static void requireRehomable(AdoptionRequest request) {
        if (request.getStatus() == AdoptionStatus.RETURNED) {
            throw new BusinessException("Este animal já voltou para quem doou");
        }
        requireStatus(request, "Só uma adoção concluída pode ser republicada", AdoptionStatus.CONCLUDED);
    }

    /** Resgate de ONG não é adoção final: não tem retorno nem repasse, e fica fora da taxa. */
    private static void requireNotNgoAdoption(AdoptionRequest request) {
        if (request.getAdopter().getUserType() == UserType.NGO) {
            throw new BusinessException("Adoções feitas por ONG não têm retorno nem repasse");
        }
    }

    /**
     * "Vale o primeiro": a linha do pedido fica travada (FOR UPDATE) até o fim,
     * e o status é conferido já com o valor do banco.
     */
    private void lock(AdoptionRequest request) {
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
    }

    private static void requireReason(ReturnReason reason, String note) {
        if (reason == null) throw new BusinessException("Escolha o motivo");
        if (reason == ReturnReason.OTHER && note == null) throw new BusinessException("Descreva o motivo");
        if (note != null && note.length() > 500) {
            throw new BusinessException("A observação pode ter no máximo 500 caracteres");
        }
    }

    private static void requireReturnDate(AdoptionRequest request, LocalDate date) {
        if (date == null) throw new BusinessException("Informe a data do retorno");
        if (date.isAfter(LocalDate.now())) throw new BusinessException("A data do retorno não pode ser futura");
        if (date.isBefore(request.getConcludedAt().toLocalDate())) {
            throw new BusinessException("A data do retorno não pode ser anterior à adoção");
        }
    }

    /**
     * O animal saiu da vitrine: os pedidos ativos dele são cancelados —
     * ANIMAL_UNAVAILABLE quando foi o dono, LISTING_REMOVED quando foi a
     * moderação. Roda na mesma transação.
     */
    @EventListener
    public void onAnimalLeftListing(AnimalLeftListingEvent event) {
        CancelReason reason = event.byModeration() ? CancelReason.LISTING_REMOVED : CancelReason.ANIMAL_UNAVAILABLE;
        requestRepository.findByAnimalIdAndStatusIn(event.animalId(), AdoptionStatus.ACTIVE)
                .forEach(request -> request.cancel(reason));
    }

    /**
     * Uma conta saiu de cena (banimento): os pedidos ativos em que ela é
     * adotante ou dona do animal são cancelados com o motivo dado. Pedidos
     * já encerrados não mudam. Roda na transação de quem chama.
     */
    @Transactional
    public void cancelAllForAccount(Long userId, CancelReason reason) {
        requestRepository.findInvolving(userId, AdoptionStatus.ACTIVE).forEach(request -> request.cancel(reason));
    }

    private static void requireRole(boolean hasRole, String message) {
        if (!hasRole) throw new ForbiddenException(message);
    }

    private static void requireStatus(AdoptionRequest request, String message, AdoptionStatus... allowed) {
        if (!Arrays.asList(allowed).contains(request.getStatus())) throw new BusinessException(message);
    }

    AdoptionRequest findRequest(Long id) {
        return requestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
    }

    /**
     * Monta os pedidos com o card do animal (as capas numa consulta só), as
     * respostas do quiz de cada adotante, o score de compatibilidade atual, o
     * anúncio novo dos repasses e as avaliações.
     */
    List<AdoptionRequestDTO> toDtos(List<AdoptionRequest> requests) {
        if (requests.isEmpty()) return List.of();

        // Um animal pode ter vários pedidos: cada card é montado uma vez só
        Map<Long, Animal> animalsById = new LinkedHashMap<>();
        requests.forEach(request -> animalsById.putIfAbsent(request.getAnimal().getId(), request.getAnimal()));
        Map<Long, AnimalSummaryDTO> cards = animalService.summarize(List.copyOf(animalsById.values())).stream()
                .collect(Collectors.toMap(AnimalSummaryDTO::id, Function.identity()));

        List<Long> adopterIds = requests.stream().map(request -> request.getAdopter().getId()).distinct().toList();
        Map<Long, MatchProfile> profiles = matchProfileRepository.findAllById(adopterIds).stream()
                .collect(Collectors.toMap(MatchProfile::getUserId, Function.identity()));

        // O anúncio novo de cada repasse, numa consulta só
        List<Long> rehomedIds = requests.stream()
                .filter(request -> request.getStatus() == AdoptionStatus.REHOMED)
                .map(AdoptionRequest::getId).toList();
        Map<Long, Long> rehomedListings = rehomedIds.isEmpty() ? Map.of() : animalRepository.findRehomedListings(rehomedIds).stream()
                .collect(Collectors.toMap(AnimalRepository.RehomedListing::getRequestId, AnimalRepository.RehomedListing::getAnimalId));

        Map<Long, List<AdoptionReview>> reviews = reviewRepository
                .findByRequestIdIn(requests.stream().map(AdoptionRequest::getId).toList()).stream()
                .collect(Collectors.groupingBy(review -> review.getRequest().getId()));

        return requests.stream().map(request -> {
            MatchProfile profile = profiles.get(request.getAdopter().getId());
            return AdoptionRequestDTO.of(
                    request,
                    cards.get(request.getAnimal().getId()),
                    AdopterDTO.from(request.getAdopter(), profile),
                    MatchScoreCalculator.score(profile, request.getAnimal()).score(),
                    rehomedListings.get(request.getId()),
                    reviewsOf(reviews.getOrDefault(request.getId(), List.of())));
        }).toList();
    }

    private static RequestReviewsDTO reviewsOf(List<AdoptionReview> reviews) {
        if (reviews.isEmpty()) return RequestReviewsDTO.NONE;
        return new RequestReviewsDTO(
                reviews.stream().filter(review -> review.getAuthorRole() == ReviewRole.ADOPTER).findFirst().map(ReviewDTO::from).orElse(null),
                reviews.stream().filter(review -> review.getAuthorRole() == ReviewRole.OWNER).findFirst().map(ReviewDTO::from).orElse(null));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
    }
}
