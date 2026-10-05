package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Instant;

import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite access tokens (JWT HS256, curta duração). Claims mínimas: subject = id do
 * usuário e {@code roles}. Nenhum dado pessoal no token.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SupriHubProperties.Jwt config;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SupriHubProperties props, Clock clock) {
        this.encoder = encoder;
        this.config = props.jwt();
        this.clock = clock;
    }

    public record AccessToken(String value, Instant expiresAt) {
    }

    public AccessToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant exp = now.plus(config.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(exp)
                .claim("roles", user.roleCodes().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), exp);
    }
}
