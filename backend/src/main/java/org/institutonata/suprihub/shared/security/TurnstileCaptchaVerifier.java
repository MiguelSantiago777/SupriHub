package org.institutonata.suprihub.shared.security;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Valida o token do Cloudflare Turnstile no servidor. Se a Cloudflare não responder, a
 * tentativa é recusada (falha fechada): o usuário só precisa tentar de novo.
 */
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);
    static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final RestClient http;

    public TurnstileCaptchaVerifier(String secretKey) {
        this(secretKey, RestClient.builder().requestFactory(factory()).build());
    }

    TurnstileCaptchaVerifier(String secretKey, RestClient http) {
        this.secretKey = secretKey;
        this.http = http;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }

    @Override
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank() || token.length() > 2048) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteIp != null) {
            form.add("remoteip", remoteIp);
        }
        try {
            Response r = http.post().uri(VERIFY_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Response.class);
            if (r == null || !r.success()) {
                log.info("Captcha recusado: {}", r == null ? "sem resposta" : r.errorCodes());
                return false;
            }
            return true;
        } catch (RestClientException e) {
            log.warn("Falha ao validar captcha na Cloudflare: {}", e.getMessage());
            return false;
        }
    }

    private static SimpleClientHttpRequestFactory factory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(10));
        return f;
    }
}
