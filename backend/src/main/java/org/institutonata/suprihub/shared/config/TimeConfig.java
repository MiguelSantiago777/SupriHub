package org.institutonata.suprihub.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@link Clock} único da aplicação — permite testes determinísticos de prazos. */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(SupriHubProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }
}
