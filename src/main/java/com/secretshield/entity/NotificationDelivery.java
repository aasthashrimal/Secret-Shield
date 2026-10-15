package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "notification_event_recipient_uk",
        columnNames = {"audit_event_id", "recipient_config_reference"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_event_id", nullable = false, updatable = false)
    private AuditEvent auditEvent;

    @Column(name = "recipient_config_reference", nullable = false, length = 32, updatable = false)
    private String recipientConfigReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "safe_error_code", length = 64)
    private String safeErrorCode;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

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
