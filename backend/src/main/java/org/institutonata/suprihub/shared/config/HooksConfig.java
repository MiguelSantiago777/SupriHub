package org.institutonata.suprihub.shared.config;

import org.institutonata.suprihub.shared.security.CaptchaVerifier;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Implementações padrão (sem efeito) dos ganchos de antivírus e captcha. */
@Configuration
public class HooksConfig {

    @Bean
    @ConditionalOnMissingBean(FileScanner.class)
    FileScanner noopFileScanner() {
        return key -> FileScanner.ScanResult.NOT_SCANNED;
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaVerifier.class)
    CaptchaVerifier noopCaptchaVerifier() {
        return (token, ip) -> true;
    }
}
