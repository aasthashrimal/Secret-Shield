package com.secretshield.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.secretshield.entity.Scan;
public interface ScanRepository extends JpaRepository<Scan,Long>, JpaSpecificationExecutor<Scan> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select scan from Scan scan where scan.id=:id")
 Optional<Scan> findLockedById(@Param("id") Long id);
 Optional<Scan> findByRepositoryIdAndRunIdAndRunAttemptAndCommitSha(Long repositoryId,String runId,Integer runAttempt,String commitSha);
 Optional<Scan> findFirstByRepositoryIdAndCommitShaOrderByCreatedAtDescIdDesc(Long repositoryId,String commitSha);
}
