package org.institutonata.suprihub.auth.api;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.auth.api.AuthDtos.EmailRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.LoginRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.MeResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.MessageResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.RegisterRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.ResetPasswordRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.SessionResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.TokenRequest;
import org.institutonata.suprihub.auth.application.AuthService;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    static final String REFRESH_COOKIE = "suprihub_rt";
    /**
     * Cookie sem segredo, legível pelo JavaScript, que só indica "há sessão": evita que o
     * frontend chame /refresh (e receba 401) em toda visita anônima.
     */
    static final String SESSION_HINT_COOKIE = "suprihub_session";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final SupriHubProperties.Jwt jwtConfig;

    public AuthController(AuthService auth, SupriHubProperties props) {
        this.auth = auth;
        this.jwtConfig = props.jwt();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cria conta de fornecedor e envia e-mail de confirmação")
    public MessageResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        auth.register(req.email(), req.password(), req.fullName(), req.captchaToken(), http.getRemoteAddr());
        return new MessageResponse("Enviamos um link de confirmação para o seu e-mail. "
                + "Abra a mensagem e clique no link para ativar sua conta.");
    }

    @PostMapping("/confirm-email")
    @Operation(summary = "Confirma o e-mail com o token recebido")
    public MessageResponse confirm(@Valid @RequestBody TokenRequest req) {
        auth.confirmEmail(req.token());
        return new MessageResponse("E-mail confirmado! Agora você já pode entrar.");
    }

    @PostMapping("/resend-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenvia o e-mail de confirmação")
    public MessageResponse resend(@Valid @RequestBody EmailRequest req) {
        auth.resendConfirmation(req.email());
        return new MessageResponse("Se houver uma conta pendente com este e-mail, enviaremos um novo link.");
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com e-mail e senha")
    public SessionResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http,
            HttpServletResponse response) {
        return toResponse(auth.login(req.email(), req.password(), req.captchaToken(), http.getRemoteAddr()), response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token a partir do cookie de sessão")
    public SessionResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            return toResponse(auth.refresh(refreshToken), response);
        } catch (RuntimeException e) {
            clearCookie(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão")
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        auth.logout(refreshToken);
        clearCookie(response);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envia link para redefinir a senha")
    public MessageResponse forgot(@Valid @RequestBody EmailRequest req, HttpServletRequest http) {
        auth.requestPasswordReset(req.email(), req.captchaToken(), http.getRemoteAddr());
        return new MessageResponse("Se houver uma conta com este e-mail, enviaremos um link para criar nova senha.");
    }

    @PostMapping("/check-reset-link")
    @Operation(summary = "Confere se o link de nova senha ainda vale, antes de a pessoa digitar a senha")
    public MessageResponse checkResetLink(@Valid @RequestBody TokenRequest req) {
        auth.checkPasswordResetLink(req.token());
        return new MessageResponse("Link válido.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Define nova senha com o token recebido")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return new MessageResponse("Senha definida com sucesso. Agora você já pode entrar.");
    }

    private SessionResponse toResponse(AuthService.Session session, HttpServletResponse response) {
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

    private void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_HINT_COOKIE, "")
                .httpOnly(false).secure(jwtConfig.secureCookie()).sameSite("Strict")
                .path("/").maxAge(Duration.ZERO).build().toString());
    }
}
