package com.aucolher.api.adoption.entity;

import com.aucolher.api.animal.entity.Animal;
import com.aucolher.api.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pedido de adoção: quem quer adotar (adopter) pede um animal ao dono dele.
 * O dono não é guardado aqui — é sempre o dono do animal.
 *
 * Um pedido "vivo" por pessoa e animal (índice uq_adoption_requests_open):
 * recusado é definitivo, e só um pedido cancelado libera um novo.
 */
@Entity
@Table(name = "adoption_requests")
@Getter
@Setter
@NoArgsConstructor
public class AdoptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adopter_id", nullable = false)
    private User adopter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdoptionStatus status = AdoptionStatus.PENDING;

    /** Só em CANCELLED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    /** Status em que o pedido estava quando foi cancelado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_from_status", length = 20)
    private AdoptionStatus cancelledFromStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "concluded_at")
    private LocalDateTime concludedAt;

    /** Quando foi recusado ou cancelado. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Score do AUmatch congelado na conclusão: nulo sem o quiz completo, para
     * ONG adotando e nos pedidos concluídos antes da migração 012.
     */
    @Column(name = "match_score_at_adoption")
    @JdbcTypeCode(SqlTypes.SMALLINT) // a coluna é SMALLINT (0 a 100)
    private Integer matchScoreAtAdoption;

    // ===================== Retorno (RETURNED) ou repasse (REHOMED) =====================

    /** RETURNED: quando o animal voltou ao doador; REHOMED: quando o adotante republicou. */
    @Column(name = "return_date")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason", length = 30)
    private ReturnReason returnReason;

    @Column(name = "return_note", length = 500)
    private String returnNote;

    @Column(name = "return_recorded_at")
    private LocalDateTime returnRecordedAt;

    public AdoptionRequest(Animal animal, User adopter) {
        this.animal = animal;
        this.adopter = adopter;
    }

    @PrePersist
    protected void onPersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** O dono do animal — compara pelo e-mail, que é o que vem no token. */
    public boolean isOwnedBy(String email) {
        return animal.isOwnedBy(email);
    }

    public boolean isAdopter(String email) {
        return email != null && email.equals(adopter.getEmail());
    }

    // ===================== Transições (quem pode cada uma fica na Service) =====================

    public void accept() {
        status = AdoptionStatus.ACCEPTED;
    }

    public void requestDelivery() {
        status = AdoptionStatus.AWAITING_DELIVERY;
    }

    /** "Ainda não recebi": a entrega volta a ser combinada no chat. */
    public void returnToAccepted() {
        status = AdoptionStatus.ACCEPTED;
    }

    public void reject() {
        status = AdoptionStatus.REJECTED;
        closedAt = LocalDateTime.now();
    }

    /** @param frozenScore o score do AUmatch neste momento, ou null sem quiz completo */
    public void conclude(Integer frozenScore) {
        status = AdoptionStatus.CONCLUDED;
        concludedAt = LocalDateTime.now();
        matchScoreAtAdoption = frozenScore;
    }

    /** O animal voltou para quem doou. Repetir num pedido já RETURNED corrige os dados. */
    public void recordReturn(LocalDate date, ReturnReason reason, String note) {
        status = AdoptionStatus.RETURNED;
        fillReturn(date, reason, note);
    }

    /** O adotante anunciou o animal para uma nova adoção. */
    public void rehome(ReturnReason reason, String note) {
        status = AdoptionStatus.REHOMED;
        fillReturn(LocalDate.now(), reason, note);
    }

    /** "Desisti de doar": a adoção volta a valer, sem rastro do repasse. */
    public void undoRehome() {
        status = AdoptionStatus.CONCLUDED;
        returnDate = null;
        returnReason = null;
        returnNote = null;
        returnRecordedAt = null;
    }

    private void fillReturn(LocalDate date, ReturnReason reason, String note) {
        returnDate = date;
        returnReason = reason;
        returnNote = note;
        returnRecordedAt = LocalDateTime.now();
    }

    /** Guarda de onde o pedido saiu: a tela mostra, por exemplo, "cancelado durante a entrega". */
    public void cancel(CancelReason reason) {
        cancelledFromStatus = status;
        status = AdoptionStatus.CANCELLED;
        cancelReason = reason;
        closedAt = LocalDateTime.now();
    }
}
