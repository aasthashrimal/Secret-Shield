package com.secretshield.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.Scan;
import com.secretshield.entity.ScanStatus;
import com.secretshield.entity.ScanTrigger;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.ScanRepository;
import com.secretshield.scanner.LocalScanner;
import com.secretshield.scanner.ScannerFailureException;
import com.secretshield.security.ResourceAuthorization;

class LocalScanExecutionServiceTests {
    @Test
    void cleanAndFindingResultsReachTheirTerminalStates() {
        var clean = fixture(new LocalScanner.Result("8.30.0", List.of()));
        assertEquals("CLEAN", clean.service.execute(clean.user, 2, 3).status());

        var finding = new SanitizedFindingInput(
                "secret-shield-synthetic", "fixture.txt", 1, "Potential secret detected");
        var detected = fixture(new LocalScanner.Result("8.30.0", List.of(finding)));
        assertEquals("FINDINGS_DETECTED", detected.service.execute(detected.user, 2, 3).status());
        verify(detected.sink).persist(detected.scan, List.of(finding));
    }

    @Test
    void controlledToolFailureReachesFailedState() {
        LocalScanner scanner = mock(LocalScanner.class);
        when(scanner.scan(any(), any(), any())).thenThrow(new ScannerFailureException("SCANNER_TIMEOUT"));
        var fixture = fixture(scanner);

        var result = fixture.service.execute(fixture.user, 2, 3);

        assertEquals("FAILED", result.status());
        assertEquals("SCANNER_TIMEOUT", result.failureCode());
        assertEquals("8.30.0", result.scannerVersion());
    }

    private static Fixture fixture(LocalScanner.Result result) {
        LocalScanner scanner = mock(LocalScanner.class);
        when(scanner.scan(any(), any(), any())).thenReturn(result);
        return fixture(scanner);
    }

    private static Fixture fixture(LocalScanner scanner) {
        var user = User.builder().id(1L).role(UserRole.DEVELOPER).build();
        var repository = ProjectRepository.builder().id(2L).owner(user)
                .githubOwner("fixture-owner").githubName("fixture")
                .defaultBranch("main").active(true).build();
        var scan = Scan.builder().id(3L).repository(repository).branch("main")
                .commitSha("0123456789abcdef0123456789abcdef01234567")
                .trigger(ScanTrigger.MANUAL).status(ScanStatus.QUEUED).build();
        ScanRepository scans = mock(ScanRepository.class);
        when(scans.findLockedById(3L)).thenReturn(Optional.of(scan));
        when(scans.saveAndFlush(scan)).thenReturn(scan);
        ProjectRepositoryService repositories = mock(ProjectRepositoryService.class);
        when(repositories.loadActiveAuthorized(user, 2L)).thenReturn(repository);
        FindingResultSink sink = mock(FindingResultSink.class);
        var service = new LocalScanExecutionService(scans, repositories,
                new ResourceAuthorization(), scanner, List.of(sink), "8.30.0");
        return new Fixture(service, user, scan, sink);
    }

    private record Fixture(LocalScanExecutionService service, User user, Scan scan, FindingResultSink sink) {}
}
