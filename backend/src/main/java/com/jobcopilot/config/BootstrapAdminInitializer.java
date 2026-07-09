package com.jobcopilot.config;

import com.jobcopilot.entity.AppUser;
import com.jobcopilot.entity.enums.Role;
import com.jobcopilot.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the single bootstrap ADMIN account on first startup if no user exists yet.
 * Credentials come from {@code app.security.bootstrap-admin.*} and should be overridden in prod.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Security.BootstrapAdmin admin = appProperties.getSecurity().getBootstrapAdmin();
        if (appUserRepository.existsByUsername(admin.getUsername())) {
            return;
        }
        AppUser user = AppUser.builder()
                .username(admin.getUsername())
                .passwordHash(passwordEncoder.encode(admin.getPassword()))
                .email(admin.getEmail())
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        appUserRepository.save(user);
        log.info("Bootstrap admin user '{}' created. Change the password after first login.",
                admin.getUsername());
    }
}
