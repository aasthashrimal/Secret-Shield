package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "findings", uniqueConstraints = @UniqueConstraint(
        name = "findings_scan_fingerprint_uk", columnNames = {"scan_id", "fingerprint"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false, updatable = false)
    private Scan scan;

    @Column(nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Column(name = "rule_id", nullable = false, length = 200, updatable = false)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Severity severity;

    @Column(name = "file_path", nullable = false, length = 1000, updatable = false)
    private String filePath;

    @Column(nullable = false, updatable = false)
    private int line;

    @Column(name = "commit_sha", nullable = false, length = 40, updatable = false)
    private String commitSha;

    @Column(name = "sanitized_description", nullable = false, length = 1000, updatable = false)
    private String sanitizedDescription;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initializeCreatedAt() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
