package com.aucolher.api.donation.dto;

import com.aucolher.api.donation.entity.DonationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DonationResponseDTO(
        Long id,
        BigDecimal amount,
        DonationStatus status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        DonationPixDTO pix,
        DonationCampaignDTO campaign,
        DonationDonorDTO donor) {
}
