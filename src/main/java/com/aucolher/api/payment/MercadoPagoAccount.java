package com.aucolher.api.payment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mercado_pago_accounts")
public class MercadoPagoAccount {

    @Id
    @Column(name = "ngo_id")
    private Long ngoId;

    @Column(name = "mp_user_id", nullable = false, unique = true)
    private Long mpUserId;

    @Convert(converter = TokenCryptoConverter.class)
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = TokenCryptoConverter.class)
    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MercadoPagoAccountStatus status = MercadoPagoAccountStatus.CONNECTED;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getNgoId() { return ngoId; }
    public void setNgoId(Long ngoId) { this.ngoId = ngoId; }
    public Long getMpUserId() { return mpUserId; }
    public void setMpUserId(Long mpUserId) { this.mpUserId = mpUserId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isLiveMode() { return liveMode; }
    public void setLiveMode(boolean liveMode) { this.liveMode = liveMode; }
    public MercadoPagoAccountStatus getStatus() { return status; }
    public void setStatus(MercadoPagoAccountStatus status) { this.status = status; }
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
