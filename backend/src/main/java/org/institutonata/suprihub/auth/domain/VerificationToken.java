package org.institutonata.suprihub.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token de uso único enviado por e-mail (confirmação de e-mail, definição de senha, troca de e-mail). */
@Entity
@Table(name = "email_verification_token")
public class VerificationToken {

    public enum Purpose { EMAIL_CONFIRMATION, PASSWORD_RESET, EMAIL_CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Só em EMAIL_CHANGE: o endereço que passa a valer quando o link for aberto. */
    @Column(name = "new_email", length = 254)
    private String newEmail;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VerificationToken() {
    }

    public VerificationToken(Long userId, String tokenHash, Purpose purpose, Instant expiresAt, Instant now) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public VerificationToken(Long userId, String tokenHash, String newEmail, Instant expiresAt, Instant now) {
        this(userId, tokenHash, Purpose.EMAIL_CHANGE, expiresAt, now);
        this.newEmail = newEmail;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getNewEmail() {
        return newEmail;
    }
}
