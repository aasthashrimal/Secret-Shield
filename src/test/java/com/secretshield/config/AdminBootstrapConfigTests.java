package com.secretshield.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import com.secretshield.service.AdminBootstrapService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapConfigTests {
    private final AdminBootstrapService service = mock(AdminBootstrapService.class);
    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

    @Test void disabledAndUnconfiguredDoesNothing() {
        new AdminBootstrapConfig(service, context, false, "", false)
                .run(new DefaultApplicationArguments());
        verifyNoInteractions(service, context);
    }

    @Test void emailCannotAccidentallyEnableBootstrap() {
        var runner = new AdminBootstrapConfig(service, context, false,
                "private-user@example.invalid", false);
        var failure = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        assertFalse(failure.getMessage().contains("private-user"));
        verifyNoInteractions(service, context);
    }

    @Test void explicitBootstrapDelegatesWithoutLoggingOrReturningTheEmail() {
        new AdminBootstrapConfig(service, context, true,
                "private-user@example.invalid", false)
                .run(new DefaultApplicationArguments());
        verify(service).promoteExistingUser("private-user@example.invalid");
        verifyNoInteractions(context);
    }
}
