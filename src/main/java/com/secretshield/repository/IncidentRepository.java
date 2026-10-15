package com.secretshield.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.secretshield.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    Optional<Incident> findByRepositoryIdAndFingerprint(Long repositoryId, String fingerprint);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select incident from Incident incident where incident.id = :id")
    Optional<Incident> findLockedById(@Param("id") Long id);
}
