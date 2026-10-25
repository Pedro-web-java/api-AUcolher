package com.aucolher.api.donation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo quando a campanha foi apagada de vez (ON DELETE SET NULL). */
    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "campaign_title", nullable = false, length = 120)
    private String campaignTitle;

    @Column(name = "ngo_id", nullable = false)
    private Long ngoId;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status = DonationStatus.PENDING;

    @Column(name = "mp_order_id", length = 64)
    private String mpOrderId;

    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
