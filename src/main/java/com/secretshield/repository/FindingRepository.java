package com.secretshield.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secretshield.entity.Finding;

public interface FindingRepository extends JpaRepository<Finding, Long>, JpaSpecificationExecutor<Finding> {
    @Query("select finding.fingerprint from Finding finding where finding.scan.id = :scanId")
    List<String> findFingerprintsByScanId(@Param("scanId") Long scanId);

    long countByScanId(Long scanId);
}
