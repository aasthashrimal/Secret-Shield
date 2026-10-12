package com.secretshield.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.secretshield.entity.CiCredential;
public interface CiCredentialRepository extends JpaRepository<CiCredential,Long> {}
