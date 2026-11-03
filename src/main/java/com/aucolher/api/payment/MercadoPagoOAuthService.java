package com.aucolher.api.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.aucolher.api.common.ApiException;
import com.aucolher.api.common.DonationGateway;
import com.aucolher.api.common.UserDirectory;
import com.aucolher.api.payment.dto.MercadoPagoAccountDTO;

/** Vínculo OAuth da conta Mercado Pago de cada ONG, renovação e desconexão. */
@Service
public class MercadoPagoOAuthService {

    public enum CallbackResult {
        CONNECTED("connected"), DENIED("denied"), ALREADY_LINKED("already_linked"), ERROR("error");

        private final String param;

        CallbackResult(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoOAuthService.class);
    private static final String AUTHORIZATION_URL = "https://auth.mercadopago.com/authorization";
    private static final long DEFAULT_EXPIRES_IN = 15_552_000L; // ~180 dias
    private static final Duration LAZY_WINDOW = Duration.ofDays(7);

    static final String PAYMENTS_UNAVAILABLE = "Pagamentos indisponíveis no momento.";
    static final String DONATIONS_UNAVAILABLE = "Doações indisponíveis no momento.";

    private final MercadoPagoProperties props;
    private final MercadoPagoAccountRepository repository;
    private final MercadoPagoClient client;
    private final OAuthStateService stateService;
    private final UserDirectory users;
    private final DonationGateway donations;
    private final TransactionTemplate tx;

    public MercadoPagoOAuthService(MercadoPagoProperties props,
                                   MercadoPagoAccountRepository repository,
                                   MercadoPagoClient client,
                                   OAuthStateService stateService,
                                   UserDirectory users,
                                   DonationGateway donations,
                                   PlatformTransactionManager transactionManager) {
        this.props = props;
        this.repository = repository;
        this.client = client;
        this.stateService = stateService;
        this.users = users;
        this.donations = donations;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ conexão

    /** Monta a authorizationUrl; o front só faz window.location.href = authorizationUrl. */
    public String startConnection(long ngoId) {
        if (!props.oauthConfigured()) {
            throw ApiException.unavailable(PAYMENTS_UNAVAILABLE);
        }
        String state = stateService.create(ngoId);
        return AUTHORIZATION_URL
                + "?client_id=" + enc(props.clientId())
                + "&response_type=code"
                + "&platform_id=mp"
                + "&state=" + enc(state)
                + "&redirect_uri=" + enc(props.redirectUri());
    }

    /** Callback do Mercado Pago. Nunca lança: o controller sempre redireciona para o front. */
    public CallbackResult handleCallback(String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            return CallbackResult.DENIED;
        }
        Long parsed = parseState(state);
        if (parsed == null) {
            return CallbackResult.ERROR;
        }
        final long ngoId = parsed;
        if (code == null || code.isBlank() || !props.oauthConfigured() || !users.isActiveNgo(ngoId)) {
            log.warn("Callback do Mercado Pago recusado para a ONG {}", ngoId);
            return CallbackResult.ERROR;
        }

        MercadoPagoClient.TokenResponse tokens;
        try {
            tokens = client.exchangeCode(code);
        } catch (MercadoPagoApiException e) {
            log.warn("Troca do code falhou para a ONG {} (http {})", ngoId, e.getHttpStatus());
            return CallbackResult.ERROR;
        }
        if (tokens.userId() == null || tokens.refreshToken() == null) {
            log.warn("Resposta de token incompleta para a ONG {}", ngoId);
            return CallbackResult.ERROR;
        }

        try {
            CallbackResult result = tx.execute(status -> {
                Optional<MercadoPagoAccount> holder = repository.findByMpUserId(tokens.userId());
                if (holder.isPresent() && !holder.get().getNgoId().equals(ngoId)) {
                    return CallbackResult.ALREADY_LINKED;
                }
                LocalDateTime now = LocalDateTime.now();
                MercadoPagoAccount account = repository.findById(ngoId).orElseGet(MercadoPagoAccount::new);
                if (account.getNgoId() == null) {
                    account.setNgoId(ngoId);
                    account.setConnectedAt(now);
                } else {
                    account.setConnectedAt(now);
                }
                account.setMpUserId(tokens.userId());
                account.setAccessToken(tokens.accessToken());
                account.setRefreshToken(tokens.refreshToken());
                account.setExpiresAt(now.plusSeconds(expiresIn(tokens)));
                account.setLiveMode(Boolean.TRUE.equals(tokens.liveMode()));
                account.setStatus(MercadoPagoAccountStatus.CONNECTED);
                account.setUpdatedAt(now);
                repository.saveAndFlush(account);
                return CallbackResult.CONNECTED;
            });
            if (result == CallbackResult.CONNECTED) {
                log.info("ONG {} conectou a conta Mercado Pago {}", ngoId, tokens.userId());
            }
            return result == null ? CallbackResult.ERROR : result;
        } catch (DataIntegrityViolationException e) {
            // duas ONGs tentando vincular a mesma conta ao mesmo tempo
            return CallbackResult.ALREADY_LINKED;
        }
    }

    private Long parseState(String state) {
        try {
            return stateService.validate(state);
        } catch (OAuthStateService.InvalidStateException | IllegalStateException e) {
            log.warn("state do OAuth recusado: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ consulta e desconexão

    @Transactional(readOnly = true)
    public MercadoPagoAccountDTO getAccount(long ngoId) {
        return repository.findById(ngoId)
                .map(MercadoPagoAccountDTO::from)
                .orElseGet(MercadoPagoAccountDTO::notConnected);
    }

    /** Usado pelo módulo de campanhas: acceptingDonations = ACTIVE e esta conta CONNECTED. */
    @Transactional(readOnly = true)
    public boolean isConnected(long ngoId) {
        return repository.findById(ngoId)
                .map(a -> a.getStatus() == MercadoPagoAccountStatus.CONNECTED)
                .orElse(false);
    }

    @Transactional
    public void disconnect(long ngoId) {
        if (!repository.existsById(ngoId)) {
            throw ApiException.notFound("Nenhuma conta do Mercado Pago está conectada.");
        }
        if (donations.hasPendingDonations(ngoId)) {
            throw ApiException.badRequest(
                    "Há doações Pix aguardando pagamento. Tente desconectar de novo em alguns minutos.");
        }
        repository.deleteById(ngoId);
        log.info("ONG {} desconectou a conta Mercado Pago", ngoId);
    }

    // ------------------------------------------------------------------ token e renovação

    /**
     * Token pronto para usar (criar order, consultar order). Renova antes se faltarem menos de 7 dias.
     * Conta que não está CONNECTED: 400 "Doações indisponíveis no momento."
     */
    public String getValidAccessToken(long ngoId) {
        refreshIfDue(ngoId, LAZY_WINDOW);
        MercadoPagoAccount account = repository.findById(ngoId)
                .orElseThrow(() -> ApiException.badRequest(DONATIONS_UNAVAILABLE));
        if (account.getStatus() != MercadoPagoAccountStatus.CONNECTED) {
            throw ApiException.badRequest(DONATIONS_UNAVAILABLE);
        }
        if (!account.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw ApiException.unavailable(PAYMENTS_UNAVAILABLE); // vencido e a renovação falhou por erro temporário
        }
        return account.getAccessToken();
    }

    /**
     * Renova se a conta vence dentro da janela. true = a conta continua utilizável.
     * Falha de autenticação (invalid_grant, 401) = NEEDS_RECONNECT, sem apagar as credenciais.
     * Falha temporária (rede, 5xx) não muda o status.
     */
    public boolean refreshIfDue(long ngoId, Duration window) {
        Boolean usable = tx.execute(status -> {
            Optional<MercadoPagoAccount> found = repository.findForUpdate(ngoId);
            if (found.isEmpty()) {
                return false;
            }
            MercadoPagoAccount account = found.get();
            if (account.getStatus() != MercadoPagoAccountStatus.CONNECTED) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            if (account.getExpiresAt().isAfter(now.plus(window))) {
                return true; // ainda não é hora (ou outra requisição já renovou)
            }
            try {
                MercadoPagoClient.TokenResponse tokens = client.refresh(account.getRefreshToken());
                account.setAccessToken(tokens.accessToken());
                if (tokens.refreshToken() != null) {
                    account.setRefreshToken(tokens.refreshToken()); // muda a cada renovação
                }
                account.setExpiresAt(now.plusSeconds(expiresIn(tokens)));
                account.setUpdatedAt(now);
                repository.save(account);
                return true;
            } catch (MercadoPagoApiException e) {
                if (e.isAuthFailure()) {
                    account.setStatus(MercadoPagoAccountStatus.NEEDS_RECONNECT);
                    account.setUpdatedAt(now);
                    repository.save(account);
                    log.warn("Renovação recusada para a ONG {} (mp_user_id {}): precisa reconectar",
                            ngoId, account.getMpUserId());
                    return false;
                }
                log.warn("Renovação falhou por erro temporário para a ONG {} (http {})", ngoId, e.getHttpStatus());
                return account.getExpiresAt().isAfter(now);
            }
        });
        return Boolean.TRUE.equals(usable);
    }

    private static long expiresIn(MercadoPagoClient.TokenResponse tokens) {
        return tokens.expiresIn() != null && tokens.expiresIn() > 0 ? tokens.expiresIn() : DEFAULT_EXPIRES_IN;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
