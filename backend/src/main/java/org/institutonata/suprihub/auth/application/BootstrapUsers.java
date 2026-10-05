package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro ADMIN a partir de variáveis de ambiente (se ainda não houver nenhum)
 * e, apenas quando {@code suprihub.bootstrap.demo-users=true} (perfil dev), um usuário de
 * demonstração por perfil: {@code <perfil>@suprihub.local}.
 */
@Component
public class BootstrapUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUsers.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SupriHubProperties.Bootstrap config;
    private final Clock clock;

    public BootstrapUsers(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            SupriHubProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.config = props.bootstrap();
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (config == null) {
            return;
        }
        if (!users.existsWithRole(AppRole.ADMIN) && notBlank(config.adminEmail()) && notBlank(config.adminPassword())) {
            create(config.adminEmail(), "Administrador do sistema", config.adminPassword(), Set.of(AppRole.ADMIN));
            log.info("Usuário ADMIN inicial criado.");
        }
        if (config.demoUsers()) {
            for (AppRole role : AppRole.values()) {
                String email = role.name().toLowerCase().replace('_', '-') + "@suprihub.local";
                if (users.findByEmail(email).isEmpty()) {
                    create(email, "Demonstração " + role.name(), config.demoPassword(), Set.of(role));
                }
            }
            log.warn("Usuários de demonstração ativos (perfil dev). Não use em produção.");
        }
    }

    private void create(String email, String name, String password, Set<AppRole> roleCodes) {
        Instant now = clock.instant();
        AppUser user = new AppUser(email, passwordEncoder.encode(password), name, now);
        user.replaceRoles(roles.findByCodeIn(roleCodes), now);
        user.confirmEmail(now);
        users.save(user);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
