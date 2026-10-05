package org.institutonata.suprihub.auth.domain;

/** Tipos de eventos de domínio do módulo de autenticação (routing keys). */
public final class AuthEvents {

    public static final String AGGREGATE = "app_user";

    /** Novo cadastro — consumidor envia e-mail de confirmação. */
    public static final String USER_REGISTERED = "auth.user.registered";
    /** Tentativa de cadastro com e-mail já existente — avisa o titular (sem revelar ao solicitante). */
    public static final String REGISTRATION_DUPLICATE = "auth.registration.duplicate";
    /** Pedido de reenvio do e-mail de confirmação. */
    public static final String EMAIL_CONFIRMATION_REQUESTED = "auth.email-confirmation.requested";
    public static final String EMAIL_CONFIRMED = "auth.user.email-confirmed";
    /** Esqueci minha senha. */
    public static final String PASSWORD_RESET_REQUESTED = "auth.password-reset.requested";
    /** Usuário interno criado pelo ADMIN — recebe link para definir a senha. */
    public static final String USER_INVITED = "auth.user.invited";

    private AuthEvents() {
    }
}
