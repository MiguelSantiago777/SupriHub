package org.institutonata.suprihub.shared.config;

import org.institutonata.suprihub.shared.security.CaptchaVerifier;
import org.institutonata.suprihub.shared.security.TurnstileCaptchaVerifier;
import org.institutonata.suprihub.shared.storage.ClamAvFileScanner;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Antivírus e captcha: ligados quando configurados ({@code CLAMAV_HOST},
 * {@code CAPTCHA_SITE_KEY}/{@code CAPTCHA_SECRET_KEY}); sem configuração, não fazem nada.
 */
@Configuration
public class HooksConfig {

    private static final Logger log = LoggerFactory.getLogger(HooksConfig.class);

    @Bean
    FileScanner fileScanner(SupriHubProperties props, FileStorage storage) {
        SupriHubProperties.Antivirus av = props.antivirus();
        if (av == null || !av.enabled()) {
            log.warn("Antivírus desligado (CLAMAV_HOST vazio): arquivos enviados não são verificados.");
            return key -> FileScanner.ScanResult.NOT_SCANNED;
        }
        return new ClamAvFileScanner(storage, av.clamavHost(), av.clamavPort(), av.timeout());
    }

    @Bean
    CaptchaVerifier captchaVerifier(SupriHubProperties props) {
        SupriHubProperties.Captcha captcha = props.captcha();
        if (captcha == null || !captcha.enabled()) {
            log.warn("Captcha desligado (CAPTCHA_SITE_KEY/CAPTCHA_SECRET_KEY vazios).");
            return (token, ip) -> true;
        }
        return new TurnstileCaptchaVerifier(captcha.secretKey());
    }
}
