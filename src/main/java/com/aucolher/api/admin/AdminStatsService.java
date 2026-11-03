package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminStatsDTO;
import com.aucolher.api.admin.dto.AdminStatsDTO.*;
import com.aucolher.api.adoption.AdoptionRequestRepository;
import com.aucolher.api.adoption.entity.AdoptionRequest;
import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.animal.entity.AnimalStatus;
import com.aucolher.api.animal.entity.Species;
import com.aucolher.api.campaign.CampaignRepository;
import com.aucolher.api.campaign.entity.Campaign;
import com.aucolher.api.donation.DonationRepository;
import com.aucolher.api.donation.entity.Donation;
import com.aucolher.api.donation.entity.DonationStatus;
import com.aucolher.api.event.EventRepository;
import com.aucolher.api.event.entity.Event;
import com.aucolher.api.event.entity.EventStatus;
import com.aucolher.api.report.ReportRepository;
import com.aucolher.api.report.entity.ReportStatus;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Os números da Visão geral do admin. O volume do TCC é pequeno, então as
 * listas são carregadas uma vez e contadas aqui — fica mais fácil de ler e
 * de testar do que uma consulta por número.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final int SERIES_MONTHS = 12;
    static final int RANKING_LIMIT = 5;
    static final int RECENT_ACTIVITY_LIMIT = 8;

    private static final Set<AdoptionStatus> ACCEPTED_OR_LATER = EnumSet.of(AdoptionStatus.ACCEPTED,
            AdoptionStatus.AWAITING_DELIVERY, AdoptionStatus.CONCLUDED, AdoptionStatus.RETURNED, AdoptionStatus.REHOMED);
    private static final Set<AdoptionStatus> DELIVERY_OR_LATER = EnumSet.of(AdoptionStatus.AWAITING_DELIVERY,
            AdoptionStatus.CONCLUDED, AdoptionStatus.RETURNED, AdoptionStatus.REHOMED);

    private final UserRepository userRepository;
    private final AnimalRepository animalRepository;
    private final AdoptionRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final CampaignRepository campaignRepository;
    private final DonationRepository donationRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public AdminStatsDTO stats() {
        List<User> accounts = userRepository.findByUserTypeNotOrderByCreatedAtDescIdDesc(UserType.ADMIN);
        List<Animal> animals = animalRepository.findAllByOrderByCreatedAtDescIdDesc();
        List<AdoptionRequest> requests = requestRepository.findAllByOrderByCreatedAtDescIdDesc();
        List<Event> events = eventRepository.findAllByOrderByDateDescStartTimeDesc();
        List<Campaign> campaigns = campaignRepository.findAllByOrderByCreatedAtDesc();
        List<Donation> approved = donationRepository.findByStatus(DonationStatus.APPROVED);
        List<String> months = lastMonths(YearMonth.now());

        Map<Long, Boolean> activeById = accounts.stream()
                .collect(Collectors.toMap(User::getId, user -> Boolean.TRUE.equals(user.getActive())));
        Predicate<Event> ngoActive = event -> activeById.getOrDefault(event.getNgoId(), false);
        Predicate<Campaign> campaignNgoActive = campaign -> activeById.getOrDefault(campaign.getNgoId(), false);
        Map<Long, String> names = accounts.stream().collect(Collectors.toMap(User::getId, User::getName));

        return new AdminStatsDTO(
                users(accounts, months),
                animals(animals, requests),
                adoptions(requests, months),
                donations(approved, months),
                campaigns(campaigns, campaignNgoActive),
                events(events, ngoActive),
                new NgoRanking(ngoRankingByRaised(approved, names, requests), ngoRankingByAdoptions(requests)),
                recentActivity(accounts, requests, events, campaigns),
                new Reports(reportRepository.countByStatus(ReportStatus.PENDING), reportRepository.countPendingTargets()),
                returns(requests));
    }

    // ===================== Blocos =====================

    static Users users(List<User> accounts, List<String> months) {
        return new Users(
                accounts.size(),
                count(accounts, user -> user.getUserType() == UserType.PERSON),
                count(accounts, user -> user.getUserType() == UserType.NGO),
                months.stream().map(month -> {
                    List<User> created = accounts.stream().filter(user -> month.equals(monthOf(user.getCreatedAt()))).toList();
                    return new NewUsers(month,
                            count(created, user -> user.getUserType() == UserType.PERSON),
                            count(created, user -> user.getUserType() == UserType.NGO));
                }).toList());
    }

    /**
     * Anúncio de conta banida não conta como disponível nem em andamento (saiu
     * do site); os adotados e os removidos contam sempre.
     */
    static Animals animals(List<Animal> animals, List<AdoptionRequest> requests) {
        Set<Long> inProgressIds = requests.stream()
                .filter(request -> AdminService.IN_PROGRESS.contains(request.getStatus()))
                .map(request -> request.getAnimal().getId())
                .collect(Collectors.toSet());
        List<Animal> onSite = animals.stream().filter(animal -> Boolean.TRUE.equals(animal.getOwner().getActive())).toList();
        List<Animal> visible = onSite.stream()
                .filter(animal -> animal.getStatus() != AnimalStatus.INACTIVE && animal.getStatus() != AnimalStatus.REMOVED)
                .toList();

        Map<Species, Long> bySpecies = new EnumMap<>(Species.class);
        for (Species species : Species.values()) {
            bySpecies.put(species, count(visible, animal -> animal.getSpecies() == species));
        }
        return new Animals(
                count(onSite, animal -> animal.getStatus() == AnimalStatus.AVAILABLE && !inProgressIds.contains(animal.getId())),
                count(onSite, animal -> animal.getStatus() == AnimalStatus.AVAILABLE && inProgressIds.contains(animal.getId())),
                count(animals, animal -> animal.getStatus() == AnimalStatus.ADOPTED),
                count(animals, animal -> animal.getStatus() == AnimalStatus.REMOVED),
                bySpecies);
    }

    static Adoptions adoptions(List<AdoptionRequest> requests, List<String> months) {
        List<AdoptionRequest> happened = happened(requests).toList();
        return new Adoptions(
                new Funnel(
                        requests.size(),
                        count(requests, request -> ACCEPTED_OR_LATER.contains(request.getStatus())),
                        count(requests, request -> DELIVERY_OR_LATER.contains(request.getStatus())),
                        happened.size()),
                months.stream()
                        .map(month -> new MonthCount(month, count(happened, request -> month.equals(monthOf(request.getConcludedAt())))))
                        .toList());
    }

    /**
     * Só as doações APPROVED, pelo mês da aprovação, em reais inteiros. As de
     * campanhas excluídas continuam contando: o dinheiro entrou.
     */
    static Donations donations(List<Donation> approved, List<String> months) {
        return new Donations(
                sumOf(approved),
                approved.size(),
                months.stream().map(month -> {
                    List<Donation> inMonth = approved.stream()
                            .filter(donation -> month.equals(monthOf(donation.getApprovedAt())))
                            .toList();
                    return new Raised(month, sumOf(inMonth), inMonth.size());
                }).toList());
    }

    /**
     * `active` e `closed` não contam as excluídas nem as de ONG banida (essas
     * vão em `suspended`); `removed` conta as excluídas que continuam no banco.
     */
    static Campaigns campaigns(List<Campaign> campaigns, Predicate<Campaign> ngoActive) {
        Predicate<Campaign> listed = campaign -> !campaign.isDeleted() && ngoActive.test(campaign);
        return new Campaigns(
                count(campaigns, listed.and(campaign -> !campaign.isClosed())),
                count(campaigns, listed.and(Campaign::isClosed)),
                count(campaigns, Campaign::isDeleted),
                count(campaigns, campaign -> !campaign.isDeleted() && !ngoActive.test(campaign)));
    }

    /** `upcoming` e `past` não contam os de ONG banida: esses vão em `suspended`. */
    static Events events(List<Event> events, Predicate<Event> ngoActive) {
        LocalDate today = LocalDate.now();
        Predicate<Event> listed = event -> event.getStatus() == EventStatus.ACTIVE && ngoActive.test(event);
        return new Events(
                count(events, listed.and(event -> !event.getDate().isBefore(today))),
                count(events, listed.and(event -> event.getDate().isBefore(today))),
                count(events, event -> event.getStatus() == EventStatus.CANCELLED),
                count(events, event -> event.getStatus() == EventStatus.ACTIVE && !ngoActive.test(event)));
    }

    /** As ONGs com mais adoções de animais delas (até 5, só com valor maior que zero). */
    static List<RankedNgo> ngoRankingByAdoptions(List<AdoptionRequest> requests) {
        Map<Long, String> names = new HashMap<>();
        Map<Long, Long> byNgo = happened(requests)
                .map(request -> request.getAnimal().getOwner())
                .filter(owner -> owner.getUserType() == UserType.NGO)
                .peek(owner -> names.put(owner.getId(), owner.getName()))
                .collect(Collectors.groupingBy(User::getId, LinkedHashMap::new, Collectors.counting()));
        return byNgo.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(RANKING_LIMIT)
                .map(entry -> new RankedNgo(entry.getKey(), names.get(entry.getKey()), 0, entry.getValue()))
                .toList();
    }

    /**
     * As ONGs que mais arrecadaram (até 5, só com valor maior que zero), com as
     * adoções de cada uma pelo mesmo cálculo do ranking de adoções.
     */
    static List<RankedNgo> ngoRankingByRaised(List<Donation> approved, Map<Long, String> names,
                                              List<AdoptionRequest> requests) {
        Map<Long, Long> adoptions = adoptionsByNgo(requests);
        Map<Long, List<Donation>> byNgo = approved.stream().collect(Collectors.groupingBy(Donation::getNgoId));
        return byNgo.entrySet().stream()
                .map(entry -> new RankedNgo(entry.getKey(), names.get(entry.getKey()), sumOf(entry.getValue()),
                        adoptions.getOrDefault(entry.getKey(), 0L)))
                .filter(ngo -> ngo.raised() > 0)
                .sorted(Comparator.comparingLong(RankedNgo::raised).reversed())
                .limit(RANKING_LIMIT)
                .toList();
    }

    /** Os 8 acontecimentos mais recentes: contas criadas, adoções, eventos e campanhas criados. */
    static List<Activity> recentActivity(List<User> accounts, List<AdoptionRequest> requests, List<Event> events,
                                         List<Campaign> campaigns) {
        Map<Long, String> names = accounts.stream().collect(Collectors.toMap(User::getId, User::getName));
        return Stream.of(
                        accounts.stream().map(user -> new Activity("ACCOUNT_CREATED", user.getCreatedAt(), user.getId(),
                                user.getName(), user.getUserType().name())),
                        happened(requests).map(request -> new Activity("ADOPTION_CONCLUDED", request.getConcludedAt(),
                                request.getAnimal().getId(), request.getAnimal().getName(), request.getAdopter().getName())),
                        events.stream().map(event -> new Activity("EVENT_CREATED", event.getCreatedAt(), event.getId(),
                                event.getTitle(), names.get(event.getNgoId()))),
                        campaigns.stream().map(campaign -> new Activity("CAMPAIGN_CREATED", campaign.getCreatedAt(),
                                campaign.getId(), campaign.getTitle(), names.get(campaign.getNgoId()))))
                .flatMap(Function.identity())
                .filter(activity -> activity.at() != null)
                .sorted(Comparator.comparing(Activity::at).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    /**
     * Taxa de reabandono (contrato do reabandono §4): todas as adoções que
     * aconteceram, menos as de adotante ONG (resgate não é adoção final),
     * divididas pelo score congelado na conclusão.
     */
    static Returns returns(List<AdoptionRequest> requests) {
        List<AdoptionRequest> adoptions = happened(requests)
                .filter(request -> request.getAdopter().getUserType() != UserType.NGO)
                .toList();
        Predicate<AdoptionRequest> isReturn = request -> request.getStatus() == AdoptionStatus.RETURNED
                || request.getStatus() == AdoptionStatus.REHOMED;
        List<AdoptionRequest> returns = adoptions.stream().filter(isReturn).toList();
        List<AdoptionRequest> noScore = adoptions.stream().filter(request -> request.getMatchScoreAtAdoption() == null).toList();

        List<ScoreRange> byRange = Stream.of(
                        new ScoreRange("HIGH", 80, 100, 0, 0, null),
                        new ScoreRange("MEDIUM", 50, 79, 0, 0, null),
                        new ScoreRange("LOW", 0, 49, 0, 0, null))
                .map(range -> {
                    List<AdoptionRequest> inRange = adoptions.stream().filter(request -> {
                        Integer score = request.getMatchScoreAtAdoption();
                        return score != null && score >= range.min() && score <= range.max();
                    }).toList();
                    long inRangeReturns = count(inRange, isReturn);
                    return new ScoreRange(range.range(), range.min(), range.max(), inRange.size(), inRangeReturns,
                            rateOf(inRangeReturns, inRange.size()));
                })
                .toList();

        Map<ReturnReason, Long> byReason = new EnumMap<>(ReturnReason.class);
        for (ReturnReason reason : ReturnReason.values()) {
            byReason.put(reason, count(returns, request -> request.getReturnReason() == reason));
        }

        return new Returns(
                adoptions.size(),
                count(adoptions, request -> request.getStatus() == AdoptionStatus.RETURNED),
                count(adoptions, request -> request.getStatus() == AdoptionStatus.REHOMED),
                rateOf(returns.size(), adoptions.size()),
                byRange,
                new NoScore(noScore.size(), count(noScore, isReturn)),
                byReason);
    }

    // ===================== Apoio =====================

    /** Os últimos meses, do mais antigo ao atual, como "YYYY-MM". */
    static List<String> lastMonths(YearMonth current) {
        return Stream.iterate(current.minusMonths(SERIES_MONTHS - 1L), month -> month.plusMonths(1))
                .limit(SERIES_MONTHS)
                .map(YearMonth::toString)
                .toList();
    }

    /** Porcentagem com uma casa decimal; nula quando não há base. */
    static Double rateOf(long part, long total) {
        return total == 0 ? null : Math.round(part * 1000.0 / total) / 10.0;
    }

    /** Adoções que aconteceram com animais de cada ONG, pelo id da ONG. */
    private static Map<Long, Long> adoptionsByNgo(List<AdoptionRequest> requests) {
        return happened(requests)
                .map(request -> request.getAnimal().getOwner())
                .filter(owner -> owner.getUserType() == UserType.NGO)
                .collect(Collectors.groupingBy(User::getId, Collectors.counting()));
    }

    /** Soma em reais inteiros (o contrato não tem centavos). */
    private static long sumOf(List<Donation> donations) {
        return donations.stream().map(Donation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
    }

    private static Stream<AdoptionRequest> happened(List<AdoptionRequest> requests) {
        return requests.stream().filter(request -> AdoptionStatus.HAPPENED.contains(request.getStatus()));
    }

    private static String monthOf(LocalDateTime moment) {
        return moment == null ? null : YearMonth.from(moment).toString();
    }

    private static <T> long count(Collection<T> items, Predicate<T> predicate) {
        return items.stream().filter(predicate).count();
    }
}
