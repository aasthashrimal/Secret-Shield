package com.secretshield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.Scan;
import com.secretshield.entity.ScanStatus;
import com.secretshield.entity.ScanTrigger;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.exception.ConflictException;
import com.secretshield.repository.ProjectRepositoryRepository;
import com.secretshield.repository.AuditEventRepository;
import com.secretshield.repository.ScanRepository;
import com.secretshield.repository.UserRepository;
import com.secretshield.scanner.LocalScanner;
import com.secretshield.service.LocalScanExecutionService;
import com.secretshield.support.PostgresApiTest;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LocalScanConcurrencyIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired ScanRepository scans;
    @Autowired AuditEventRepository auditEvents;
    @Autowired LocalScanExecutionService execution;
    @Autowired TransactionTemplate transaction;
    @MockitoBean LocalScanner scanner;
    private Fixture createdFixture;

    @Test
    void simultaneousStartsRunTheScannerOnceAndRejectTheDuplicate() throws Exception {
        Fixture fixture = transaction.execute(status -> {
            User user = users.saveAndFlush(User.builder().username("concurrent-local")
                    .email("concurrent-local@example.invalid").password("hash")
                    .role(UserRole.DEVELOPER).build());
            ProjectRepository repository = repositories.saveAndFlush(ProjectRepository.builder()
                    .owner(user).githubOwner("fixture-owner").githubName("concurrent-fixture")
                    .canonicalUrl("https://github.com/fixture-owner/concurrent-fixture")
                    .defaultBranch("main").active(true).build());
            Scan scan = scans.saveAndFlush(Scan.builder().repository(repository).branch("main")
                    .commitSha("0123456789abcdef0123456789abcdef01234567")
                    .trigger(ScanTrigger.MANUAL).status(ScanStatus.QUEUED).build());
            return new Fixture(user.getId(), repository.getId(), scan.getId());
        });
        createdFixture = fixture;

        CountDownLatch enteredScanner = new CountDownLatch(1);
        CountDownLatch releaseScanner = new CountDownLatch(1);
        when(scanner.scan(any(), any(), any())).thenAnswer(invocation -> {
            enteredScanner.countDown();
            if (!releaseScanner.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Test scanner was not released");
            }
            return new LocalScanner.Result("8.30.0", List.of());
        });

        User actor = users.findById(fixture.userId).orElseThrow();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> execution.execute(actor, fixture.repositoryId, fixture.scanId));
            org.junit.jupiter.api.Assertions.assertTrue(enteredScanner.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> execution.execute(actor, fixture.repositoryId, fixture.scanId));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            releaseScanner.countDown();

            assertEquals("CLEAN", first.get(5, TimeUnit.SECONDS).status());
            ExecutionException duplicate = assertThrows(
                    ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            assertInstanceOf(ConflictException.class, duplicate.getCause());
        } finally {
            releaseScanner.countDown();
        }
        verify(scanner, times(1)).scan(any(), any(), any());
    }

    @AfterEach
    void removeCommittedFixture() {
        if (createdFixture == null) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            auditEvents.deleteAll(auditEvents.findAll((root, query, cb) ->
                    cb.equal(root.get("repository").get("id"), createdFixture.repositoryId)));
            auditEvents.flush();
            scans.deleteById(createdFixture.scanId);
            scans.flush();
            repositories.deleteById(createdFixture.repositoryId);
            repositories.flush();
            users.deleteById(createdFixture.userId);
            users.flush();
        });
    }

    private record Fixture(long userId, long repositoryId, long scanId) {}
}
