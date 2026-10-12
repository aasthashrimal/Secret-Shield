package com.secretshield.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secretshield.entity.ProjectRepository;

public interface ProjectRepositoryRepository extends JpaRepository<ProjectRepository, Long> {
    boolean existsByOwnerIdAndGithubOwnerAndGithubName(
            Long ownerId, String githubOwner, String githubName);

    @Query("""
            SELECT repository FROM ProjectRepository repository
            WHERE (:ownerId IS NULL OR repository.owner.id = :ownerId)
              AND (:active IS NULL OR repository.active = :active)
              AND (lower(repository.githubOwner) LIKE concat('%', :query, '%')
                   OR lower(repository.githubName) LIKE concat('%', :query, '%'))
            """)
    Page<ProjectRepository> findAuthorized(
            @Param("ownerId") Long ownerId,
            @Param("active") Boolean active,
            @Param("query") String query,
            Pageable pageable);
}
