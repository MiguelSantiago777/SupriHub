package org.institutonata.suprihub.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Segunda etapa do login: a senha já conferiu e falta o código de 6 dígitos enviado por
 * e-mail. Vale 10 minutos e aceita 5 tentativas; depois disso é preciso entrar de novo.
 */
@Entity
@Table(name = "login_challenge")
public class LoginChallenge {

    public static final Duration TTL = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
    private String challengeHash;

    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginChallenge() {
    }

    public LoginChallenge(Long userId, String challengeHash, Instant now) {
        this.userId = userId;
        this.challengeHash = challengeHash;
        this.expiresAt = now.plus(TTL);
        this.createdAt = now;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && attempts < MAX_ATTEMPTS && expiresAt.isAfter(now);
    }

    /** Novo código (reenvio): o anterior deixa de valer e o prazo recomeça. */
    public void newCode(String codeHash, Instant now) {
        this.codeHash = codeHash;
        this.lastSentAt = now;
        this.expiresAt = now.plus(TTL);
    }

    public boolean canResend(Instant now) {
        return lastSentAt == null || !lastSentAt.plus(RESEND_INTERVAL).isAfter(now);
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }
}
