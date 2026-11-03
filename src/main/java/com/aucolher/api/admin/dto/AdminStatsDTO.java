package com.aucolher.api.admin.dto;

import com.aucolher.api.adoption.entity.ReturnReason;
import com.aucolher.api.animal.entity.Species;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Números e gráficos da Visão geral do admin (e do PDF). As séries mensais
 * cobrem sempre os últimos 12 meses ("YYYY-MM"), do mais antigo ao atual,
 * com zero onde não houve nada.
 *
 * `donations` e `ngoRanking.byRaised` contam só as doações APPROVED, em reais
 * inteiros, inclusive as de campanhas excluídas.
 */
public record AdminStatsDTO(
        Users users,
        Animals animals,
        Adoptions adoptions,
        Donations donations,
        Campaigns campaigns,
        Events events,
        NgoRanking ngoRanking,
        List<Activity> recentActivity,
        Reports reports,
        Returns returns
) {

    public record Users(long total, long persons, long ngos, List<NewUsers> newByMonth) {
    }

    public record NewUsers(String month, long persons, long ngos) {
    }

    /** Sem os anúncios fora do ar nem os de conta banida; os adotados contam sempre. */
    public record Animals(long available, long inProgress, long adopted, long removed, Map<Species, Long> bySpecies) {
    }

    public record Adoptions(Funnel funnel, List<MonthCount> concludedByMonth) {
    }

    /** Cada etapa conta quem chegou nela ou passou dela. */
    public record Funnel(long received, long accepted, long delivery, long concluded) {
    }

    public record MonthCount(String month, long count) {
    }

    public record Donations(long totalRaised, long approvedCount, List<Raised> raisedByMonth) {
    }

    public record Raised(String month, long amount, long count) {
    }

    public record Campaigns(long active, long closed, long removed, long suspended) {
    }

    public record Events(long upcoming, long past, long cancelled, long suspended) {
    }

    public record NgoRanking(List<RankedNgo> byRaised, List<RankedNgo> byAdoptions) {
    }

    public record RankedNgo(Long id, String name, long raised, long adoptions) {
    }

    /** ACCOUNT_CREATED, ADOPTION_CONCLUDED, CAMPAIGN_CREATED ou EVENT_CREATED — `id` é do que foi criado. */
    public record Activity(String type, LocalDateTime at, Long id, String title, String detail) {
    }

    /** Denúncias em análise e quantos alvos diferentes elas atingem ("casos"). */
    public record Reports(long pending, long pendingTargets) {
    }

    /**
     * Taxa de reabandono, sem as adoções feitas por ONG. `rate` em %, com uma
     * casa decimal, e nulo sem adoções. Faixas pelo score congelado na conclusão.
     */
    public record Returns(long adoptions, long returned, long rehomed, Double rate,
                          List<ScoreRange> byRange, NoScore noScore, Map<ReturnReason, Long> byReason) {
    }

    public record ScoreRange(String range, int min, int max, long adoptions, long returns, Double rate) {
    }

    public record NoScore(long adoptions, long returns) {
    }
}
