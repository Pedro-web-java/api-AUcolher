package com.aucolher.api.payment.dto;

import java.time.LocalDateTime;

import com.aucolher.api.payment.MercadoPagoAccount;
import com.aucolher.api.payment.MercadoPagoAccountStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Situação da conta. Nunca leva token. connected só é true em CONNECTED. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MercadoPagoAccountDTO(
        boolean connected,
        String status,
        Long mpUserId,
        Boolean liveMode,
        LocalDateTime connectedAt) {

    public static MercadoPagoAccountDTO from(MercadoPagoAccount a) {
        return new MercadoPagoAccountDTO(
                a.getStatus() == MercadoPagoAccountStatus.CONNECTED,
                a.getStatus().name(),
                a.getMpUserId(),
                a.isLiveMode(),
                a.getConnectedAt());
    }

    public static MercadoPagoAccountDTO notConnected() {
        return new MercadoPagoAccountDTO(false, null, null, null, null);
    }
}
