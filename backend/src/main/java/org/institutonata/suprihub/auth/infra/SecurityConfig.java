package org.institutonata.suprihub.auth.infra;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança: API stateless com JWT (Bearer). O refresh token vai em cookie HttpOnly,
 * SameSite=Strict, restrito a {@code /api/auth}. O controle fino por perfil é feito com
 * {@code @PreAuthorize} em cada endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/public/**", "/api/auth/**",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'; "
                                        + "img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemEntryPoint()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemEntryPoint())
                        .accessDeniedHandler(problemAccessDenied()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecretKey jwtSecretKey(SupriHubProperties props) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("suprihub.jwt.secret deve ter pelo menos 32 bytes (256 bits).");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, SupriHubProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return authorities;
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SupriHubProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(props.frontendUrl()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Captcha-Token"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static AuthenticationEntryPoint problemEntryPoint() {
        return (request, response, ex) -> writeProblem(response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Entre com seu e-mail e senha para continuar.", "UNAUTHENTICATED");
    }

    private static AccessDeniedHandler problemAccessDenied() {
        return (request, response, ex) -> writeProblem(response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para realizar esta ação.", "ACCESS_DENIED");
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
            String title, String detail, String code) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(title, status.value(), detail, code));
    }
}
