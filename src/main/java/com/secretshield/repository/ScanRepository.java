package com.secretshield.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import com.secretshield.entity.Scan;
public interface ScanRepository extends JpaRepository<Scan,Long>, JpaSpecificationExecutor<Scan> {
 Optional<Scan> findByRepositoryIdAndRunIdAndRunAttemptAndCommitSha(Long repositoryId,String runId,Integer runAttempt,String commitSha);
}
