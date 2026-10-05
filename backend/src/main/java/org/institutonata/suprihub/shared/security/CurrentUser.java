package org.institutonata.suprihub.shared.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Acesso ao usuário autenticado (subject do JWT = id do {@code app_user}). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(Long.valueOf(jwt.getSubject()));
        }
        return Optional.empty();
    }

    public static long requireId() {
        return id().orElseThrow(() -> new IllegalStateException("Usuário não autenticado"));
    }

    /** Rótulo não pessoal para trilhas: "usuario#123" ou "sistema". */
    public static String label() {
        return id().map(i -> "usuario#" + i).orElse("sistema");
    }
}
