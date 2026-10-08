package org.institutonata.suprihub.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.auth.api.AuthDtos.ChangeEmailRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.ChangePasswordRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.MeResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.MessageResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.RenameRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.SessionResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.TwoFactorRequest;
import org.institutonata.suprihub.auth.application.AuthService;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Minha conta": o próprio usuário troca nome, senha e e-mail de acesso. */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Minha conta")
public class AccountController {

    private final AuthService auth;
    private final SessionCookies cookies;

    public AccountController(AuthService auth, SessionCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PutMapping("/name")
    @Operation(summary = "Altera o nome")
    public MeResponse rename(@Valid @RequestBody RenameRequest req) {
        return MeResponse.of(auth.rename(CurrentUser.requireId(), req.fullName()));
    }

    @PostMapping("/password")
    @Operation(summary = "Troca a senha (encerra as sessões em outros aparelhos)")
    public SessionResponse changePassword(@Valid @RequestBody ChangePasswordRequest req,
            HttpServletResponse response) {
        return cookies.write(auth.changePassword(CurrentUser.requireId(), req.currentPassword(), req.newPassword()),
                response);
    }

    @PutMapping("/two-factor")
    @Operation(summary = "Liga ou desliga o código por e-mail ao entrar (obrigatório para a equipe interna)")
    public MeResponse twoFactor(@Valid @RequestBody TwoFactorRequest req) {
        return MeResponse.of(auth.setTwoFactor(CurrentUser.requireId(), req.enabled(), req.currentPassword()));
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pede a troca do e-mail de acesso (link de confirmação vai para o novo endereço)")
    public MessageResponse changeEmail(@Valid @RequestBody ChangeEmailRequest req) {
        auth.requestEmailChange(CurrentUser.requireId(), req.newEmail(), req.currentPassword());
        return new MessageResponse("Enviamos um link de confirmação para o novo e-mail. "
                + "Seu e-mail de acesso só muda depois que você abrir esse link.");
    }
}
