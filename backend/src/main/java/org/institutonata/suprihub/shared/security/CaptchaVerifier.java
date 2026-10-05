package org.institutonata.suprihub.shared.security;

/**
 * Gancho de captcha para endpoints públicos (cadastro, login). A implementação padrão
 * aceita tudo; para ativar, registre um bean (ex.: hCaptcha/Turnstile) que valide o token.
 */
@FunctionalInterface
public interface CaptchaVerifier {

    boolean verify(String token, String remoteIp);
}
