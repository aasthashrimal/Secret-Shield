package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false, updatable = false)
    private ProjectRepository repository;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private AuditActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_credential_id", updatable = false)
    private CiCredential actorCredential;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64, updatable = false)
    private AuditOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32, updatable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "safe_change_summary", nullable = false, length = 500, updatable = false)
    private String safeChangeSummary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void initializeOccurredAt() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
