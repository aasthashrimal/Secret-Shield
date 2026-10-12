package com.secretshield.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.secretshield.service.AdminBootstrapService;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfig implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    private final AdminBootstrapService bootstrap;
    private final ConfigurableApplicationContext context;
    private final boolean enabled;
    private final String email;
    private final boolean exit;

    public AdminBootstrapConfig(AdminBootstrapService bootstrap, ConfigurableApplicationContext context,
            @Value("${secretshield.admin-bootstrap-enabled:false}") boolean enabled,
            @Value("${secretshield.admin-bootstrap-email:}") String email,
            @Value("${secretshield.admin-bootstrap-exit:false}") boolean exit) {
        this.bootstrap = bootstrap;
        this.context = context;
        this.enabled = enabled;
        this.email = email;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            if (email != null && !email.isBlank()) {
                throw new IllegalStateException("Administrator bootstrap email requires explicit enablement");
            }
            return;
        }
        bootstrap.promoteExistingUser(email);
        log.info("Administrator bootstrap completed for the configured existing user");
        if (exit) { SpringApplication.exit(context); }
    }
}
