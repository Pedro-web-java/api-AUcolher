package com.aucolher.api.payment;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Todo dia às 3h renova as contas que vencem em até 30 dias, para ONGs sem doação recente não perderem o acesso. */
@Component
public class MercadoPagoTokenRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoTokenRefreshJob.class);
    private static final Duration WINDOW = Duration.ofDays(30);

    private final MercadoPagoAccountRepository repository;
    private final MercadoPagoOAuthService service;

    public MercadoPagoTokenRefreshJob(MercadoPagoAccountRepository repository, MercadoPagoOAuthService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void renewExpiringAccounts() {
        var due = repository.findByStatusAndExpiresAtBefore(
                MercadoPagoAccountStatus.CONNECTED, LocalDateTime.now().plus(WINDOW));
        for (MercadoPagoAccount account : due) {
            try {
                service.refreshIfDue(account.getNgoId(), WINDOW);
            } catch (RuntimeException e) {
                log.warn("Falha ao renovar a conta da ONG {}: {}", account.getNgoId(), e.getClass().getSimpleName());
            }
        }
    }
}
