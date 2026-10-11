package com.secretshield;

import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.secretshield.repository.UserRepository;
import com.secretshield.support.PostgresApiTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class RegistrationRaceIT extends PostgresApiTest {
    @MockitoSpyBean PasswordEncoder encoder;
    @Autowired UserRepository users;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationReturnsOne201AndOne409() throws Exception {
        // Force both requests past the prechecks before either INSERT occurs.
        var ready = new CountDownLatch(2);
        doAnswer(call -> {
            ready.countDown();
            if (!ready.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Race test synchronization failed"); }
            return new BCryptPasswordEncoder().encode(call.getArgument(0, CharSequence.class));
        }).when(encoder).encode(any());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first = () -> postJson("/api/auth/register", Map.of("username", "synthetic-race-one",
                    "email", "race@example.invalid", "password", "synthetic-race-password"))
                    .andReturn().getResponse().getStatus();
            Callable<Integer> second = () -> postJson("/api/auth/register", Map.of("username", "synthetic-race-two",
                    "email", " RACE@EXAMPLE.INVALID ", "password", "synthetic-race-password"))
                    .andReturn().getResponse().getStatus();
            Future<Integer> a = workers.submit(first), b = workers.submit(second);
            var statuses = new java.util.ArrayList<>(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            statuses.sort(Integer::compareTo);
            assertEquals(java.util.List.of(201, 409), statuses);
            assertEquals(1, users.count());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Race workers must stop before cleanup");
            // This method commits only synthetic rows in the guarded disposable database.
            users.deleteAll();
        }
    }
}
