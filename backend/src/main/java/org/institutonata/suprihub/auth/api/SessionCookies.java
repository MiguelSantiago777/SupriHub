package org.institutonata.suprihub.auth.api;

import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;

import org.institutonata.suprihub.auth.api.AuthDtos.MeResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.SessionResponse;
import org.institutonata.suprihub.auth.application.AuthService;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Grava e apaga os cookies de sessão (login, refresh, logout e troca de senha). */
@Component
class SessionCookies {

    static final String REFRESH_COOKIE = "suprihub_rt";
    /**
     * Cookie sem segredo, legível pelo JavaScript, que só indica "há sessão": evita que o
     * frontend chame /refresh (e receba 401) em toda visita anônima.
     */
    static final String SESSION_HINT_COOKIE = "suprihub_session";
    private static final String COOKIE_PATH = "/api/auth";

    private final SupriHubProperties.Jwt jwtConfig;

    SessionCookies(SupriHubProperties props) {
        this.jwtConfig = props.jwt();
    }

    SessionResponse write(AuthService.Session session, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(jwtConfig.refreshTtl()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_HINT_COOKIE, "1")
                .httpOnly(false).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path("/").maxAge(jwtConfig.refreshTtl()).build().toString());
        return new SessionResponse(session.accessToken().value(), session.accessToken().expiresAt(),
                MeResponse.of(session.user()));
    }

    void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_HINT_COOKIE, "")
                .httpOnly(false).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path("/").maxAge(Duration.ZERO).build().toString());
    }
}
