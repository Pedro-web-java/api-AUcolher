package com.aucolher.api.donation.dto;

import java.time.LocalDateTime;

public record DonationPixDTO(String qrCode, String qrCodeBase64, String ticketUrl, LocalDateTime expiresAt) {
}
