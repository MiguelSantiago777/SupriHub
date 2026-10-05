package org.institutonata.suprihub.tender.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;

/**
 * Convite do rodízio da Faixa 1 (Política art. 13; Edital 7.1): o credenciado aceita
 * enviando o preço em até 2 dias úteis; sem aceite, passa ao seguinte. A Comissão confere
 * o preço e confirma a seleção.
 */
@Entity
@Table(name = "tender_invitation")
public class TenderInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private Long tenderId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.INVITED;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decline_reason")
    private String declineReason;

    protected TenderInvitation() {
    }

    public TenderInvitation(Long tenderId, Long applicationId, LocalDate dueDate, Instant now) {
        this.tenderId = tenderId;
        this.applicationId = applicationId;
        this.dueDate = dueDate;
        this.invitedAt = now;
    }

    public boolean canRespond(LocalDate today) {
        return status == InvitationStatus.INVITED && !today.isAfter(dueDate);
    }

    public void accept(Instant now) {
        require(InvitationStatus.INVITED);
        status = InvitationStatus.ACCEPTED;
        respondedAt = now;
    }

    public void decline(String reason, Instant now) {
        require(InvitationStatus.INVITED);
        status = InvitationStatus.DECLINED;
        declineReason = reason == null || reason.isBlank() ? null : reason.trim();
        respondedAt = now;
    }

    public void expire(Instant now) {
        require(InvitationStatus.INVITED);
        status = InvitationStatus.EXPIRED;
        respondedAt = now;
    }

    public void confirm(Instant now) {
        require(InvitationStatus.ACCEPTED);
        status = InvitationStatus.CONFIRMED;
        respondedAt = now;
    }

    public void rejectPrice(String reason, Instant now) {
        require(InvitationStatus.ACCEPTED);
        status = InvitationStatus.PRICE_REJECTED;
        declineReason = reason.trim();
        respondedAt = now;
    }

    private void require(InvitationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Convite " + status + " não admite esta ação");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTenderId() {
        return tenderId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}
