package com.secretshield.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="scans") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Scan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="repository_id",nullable=false,updatable=false) private ProjectRepository repository;
 @Column(name="commit_sha",nullable=false,length=40,updatable=false) private String commitSha;
 @Column(nullable=false,length=255,updatable=false) private String branch;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private ScanTrigger trigger;
 @Column(name="run_id",length=200,updatable=false) private String runId;
 @Column(name="run_attempt",updatable=false) private Integer runAttempt;
 @Column(name="scanner_version",length=100) private String scannerVersion;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ScanStatus status;
 @Column(name="failure_code",length=64) private String failureCode;
 @Column(name="payload_digest",length=64,updatable=false) private String payloadDigest;
 @Column(name="started_at") private LocalDateTime startedAt;
 @Column(name="completed_at") private LocalDateTime completedAt;
 @Setter(AccessLevel.NONE) @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Setter(AccessLevel.NONE) @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @PrePersist void create(){ var now=LocalDateTime.now(ZoneOffset.UTC); createdAt=now; updatedAt=now; }
 @PreUpdate void update(){ updatedAt=LocalDateTime.now(ZoneOffset.UTC); }
}
