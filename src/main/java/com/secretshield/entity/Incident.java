package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "incidents", uniqueConstraints = @UniqueConstraint(
        name = "incidents_repository_fingerprint_uk",
        columnNames = {"repository_id", "fingerprint"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false, updatable = false)
    private ProjectRepository repository;

    @Column(nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_actor_type", length = 20)
    private AuditActorType lastActorType;

    @Column(name = "last_actor_reference")
    private Long lastActorReference;

    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long version;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
