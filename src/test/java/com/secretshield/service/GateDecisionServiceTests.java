package com.secretshield.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.repository.FindingRepository;
import com.secretshield.repository.ScanRepository;

class GateDecisionServiceTests {
    @Test
    void reportingStoreOutageCannotProduceAllow() {
        User owner = User.builder().id(1L).role(UserRole.DEVELOPER).build();
        ProjectRepository repository = ProjectRepository.builder().id(2L).owner(owner).build();
        ProjectRepositoryService repositories = mock(ProjectRepositoryService.class);
        ScanRepository scans = mock(ScanRepository.class);
        when(repositories.loadAuthorized(owner, 2L)).thenReturn(repository);
        String sha = "0123456789abcdef0123456789abcdef01234567";
        when(scans.findFirstByRepositoryIdAndCommitShaOrderByCreatedAtDescIdDesc(2L, sha))
                .thenThrow(new DataAccessResourceFailureException("synthetic outage"));
        var service = new GateDecisionService(repositories, scans,
                mock(FindingRepository.class), "8.30.0");

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.decide(owner, 2L, sha));
    }
}
