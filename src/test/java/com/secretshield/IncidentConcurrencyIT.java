package com.secretshield;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.secretshield.dto.IncidentTransitionRequest;
import com.secretshield.entity.*;
import com.secretshield.exception.ConflictException;
import com.secretshield.repository.*;
import com.secretshield.service.IncidentService;
import com.secretshield.support.PostgresApiTest;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncidentConcurrencyIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository repositories;
    @Autowired IncidentRepository incidents;
    @Autowired AuditEventRepository events;
    @Autowired IncidentService service;
    @Autowired TransactionTemplate transaction;
    private Fixture fixture;

    @Test
    void simultaneousReviewsOfSameVersionCommitExactlyOneChangeAndAuditEvent() throws Exception {
        fixture = transaction.execute(status -> {
            User owner = users.saveAndFlush(User.builder().username("concurrent-incident")
                    .email("concurrent-incident@example.invalid").password("hash")
                    .role(UserRole.DEVELOPER).build());
            ProjectRepository repository = repositories.saveAndFlush(ProjectRepository.builder()
                    .owner(owner).githubOwner("fixture-owner").githubName("incident-race")
                    .canonicalUrl("https://github.com/fixture-owner/incident-race")
                    .defaultBranch("main").active(true).build());
            Incident incident = incidents.saveAndFlush(Incident.builder().repository(repository)
                    .fingerprint("0".repeat(64)).severity(Severity.LOW).status(IncidentStatus.OPEN)
                    .lastActorType(AuditActorType.SYSTEM).build());
            return new Fixture(owner.getId(), repository.getId(), incident.getId());
        });
        User actor = users.findById(fixture.userId).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> reviewAfter(start, actor, "RESOLVED"));
            var second = pool.submit(() -> reviewAfter(start, actor, "RESOLVED"));
            start.countDown();
            Object a = first.get(10, TimeUnit.SECONDS);
            Object b = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, (a instanceof ConflictException ? 1 : 0)
                    + (b instanceof ConflictException ? 1 : 0));
            assertEquals(1, (a instanceof String ? 1 : 0) + (b instanceof String ? 1 : 0));
        }
        assertEquals(1, incidents.findById(fixture.incidentId).orElseThrow().getVersion());
        assertEquals(1, events.findAll((root, query, cb) ->
                cb.equal(root.get("repository").get("id"), fixture.repositoryId)).size());
    }

    private Object reviewAfter(CountDownLatch start, User actor, String next) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            return service.transition(actor, fixture.repositoryId, fixture.incidentId,
                    new IncidentTransitionRequest(next, "Synthetic fixture reviewed", 0L)).status();
        } catch (ConflictException conflict) {
            return conflict;
        }
    }

    @AfterEach
    void removeFixture() {
        if (fixture == null) return;
        transaction.executeWithoutResult(status -> {
            events.deleteAll(events.findAll((root, query, cb) ->
                    cb.equal(root.get("repository").get("id"), fixture.repositoryId)));
            events.flush();
            incidents.deleteById(fixture.incidentId);
            incidents.flush();
            repositories.deleteById(fixture.repositoryId);
            repositories.flush();
            users.deleteById(fixture.userId);
            users.flush();
        });
    }

    private record Fixture(long userId, long repositoryId, long incidentId) {}
}
