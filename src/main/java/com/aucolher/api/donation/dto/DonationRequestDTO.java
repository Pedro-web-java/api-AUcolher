package com.aucolher.api.donation.dto;

import java.math.BigDecimal;

/** { "amount": 50 }. A validação fica no service para respeitar a ordem de recusas do contrato. */
public record DonationRequestDTO(BigDecimal amount) {
}
