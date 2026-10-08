package org.institutonata.suprihub.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class AppUser {

    /**
     * Bloqueio da conta inteira, contra tentativas distribuídas (vários IPs). O limite por
     * IP + conta, bem menor, fica no {@code LoginThrottle}: assim um terceiro não consegue
     * bloquear a conta de alguém só errando a senha algumas vezes.
     */
    public static final int MAX_FAILED_LOGINS = 20;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "sessions_revoked_at")
    private Instant sessionsRevokedAt;

    /** E-mail pedido em "Minha conta", aguardando confirmação pelo link. */
    @Column(name = "pending_email", length = 254)
    private String pendingEmail;

    /** E-mail de acesso anterior à última troca (recebe o aviso da troca). */
    @Column(name = "previous_email", length = 254)
    private String previousEmail;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String fullName, Instant now) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = PersonName.normalize(fullName);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Set<AppRole> roleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(AppRole role) {
        return roleCodes().contains(role);
    }

    public void replaceRoles(Set<Role> newRoles, Instant now) {
        this.roles.clear();
        this.roles.addAll(newRoles);
        this.updatedAt = now;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(Instant now) {
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLogins = 0;
        }
        updatedAt = now;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLogins = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void confirmEmail(Instant now) {
        emailVerified = true;
        updatedAt = now;
    }

    public void requestEmailChange(String newEmail, Instant now) {
        pendingEmail = newEmail.trim().toLowerCase();
        updatedAt = now;
    }

    public void changeEmail(String newEmail, Instant now) {
        previousEmail = email;
        email = newEmail.trim().toLowerCase();
        pendingEmail = null;
        emailVerified = true;
        updatedAt = now;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public String getPreviousEmail() {
        return previousEmail;
    }

    public void changePassword(String newHash, Instant now) {
        passwordHash = newHash;
        failedLogins = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    /** Invalida os access tokens emitidos antes de {@code now}. */
    public void revokeSessions(Instant now) {
        sessionsRevokedAt = now;
        updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void rename(String fullName, Instant now) {
        this.fullName = PersonName.normalize(fullName);
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getSessionsRevokedAt() {
        return sessionsRevokedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "AppUser#" + id; // nunca expor e-mail/nome em logs
    }
}
