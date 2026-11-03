package com.aucolher.api.user;

import com.aucolher.api.adoption.AdoptionRequestService;
import com.aucolher.api.adoption.entity.CancelReason;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.event.EventAttendanceRepository;
import com.aucolher.api.event.EventService;
import com.aucolher.api.favorite.FavoriteRepository;
import com.aucolher.api.match.MatchProfileRepository;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.report.entity.ReportResolution;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.user.dto.AccountDeletionDTO;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * A pessoa ou a ONG exclui a própria conta. Não dá para desfazer. Reaproveita
 * as peças do banimento: os pedidos ativos são cancelados e, com a conta
 * inativa, o conteúdo dela some das rotas públicas. O que é só da exclusão:
 * os anúncios, campanhas e eventos saem do ar de vez, os dados que só
 * serviam à conta são apagados e a linha de users é anonimizada (o histórico
 * das outras pessoas aponta para ela e mostra "Conta excluída").
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    static final String CONFIRMATION_WORD = "EXCLUIR";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdoptionRequestService requestService;
    private final AnimalService animalService;
    private final CampaignService campaignService;
    private final EventService eventService;
    private final MatchProfileRepository matchProfileRepository;
    private final FavoriteRepository favoriteRepository;
    private final EventAttendanceRepository attendanceRepository;
    private final ReportService reportService;

    /** Tudo numa transação, na ordem do contrato (api-exclusao-conta.md §2). */
    @Transactional
    public void deleteAccount(String email, AccountDeletionDTO body) {
        User account = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        if (account.getUserType() == UserType.ADMIN) {
            throw new ForbiddenException("A conta de administrador não pode ser excluída.");
        }
        confirm(account, body);

        Long id = account.getId();
        LocalDateTime now = LocalDateTime.now();

        // Antes dos animais: senão o "saiu da vitrine" cancelaria os pedidos como ANIMAL_UNAVAILABLE
        requestService.cancelAllForAccount(id, CancelReason.ACCOUNT_DELETED);
        animalService.unlistAllOf(id);
        campaignService.closeAndHideAllOf(id);
        eventService.cancelUpcomingOf(id);

        matchProfileRepository.deleteById(id);
        favoriteRepository.removeAllOf(id);
        attendanceRepository.deleteAllOf(id);

        // Lida de novo: cancelar os PIX das campanhas limpa o contexto do JPA
        User deleted = userRepository.findById(id).orElseThrow();
        deleted.anonymize(now);
        userRepository.save(deleted);

        reportService.resolveAgainstAccount(id, ReportResolution.DISMISSED, null, now);
    }

    /** Conta com senha confirma com a senha; conta do Google sem senha, digitando EXCLUIR. */
    private void confirm(User account, AccountDeletionDTO body) {
        if (account.getPassword() != null) {
            String password = body == null ? null : body.password();
            if (password == null || !passwordEncoder.matches(password, account.getPassword())) {
                throw new BusinessException("Senha incorreta.");
            }
            return;
        }
        String confirmation = body == null || body.confirmation() == null ? "" : body.confirmation().trim();
        if (!CONFIRMATION_WORD.equals(confirmation)) {
            throw new BusinessException("Digite EXCLUIR para confirmar.");
        }
    }
}
