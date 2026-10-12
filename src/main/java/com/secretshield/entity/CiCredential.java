package com.secretshield.entity;
import java.time.*;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="ci_credentials") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CiCredential {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="repository_id",nullable=false,updatable=false) private ProjectRepository repository;
 @Column(name="secret_hash",nullable=false,length=100,updatable=false) private String secretHash;
 @Setter(AccessLevel.NONE) @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="expires_at",updatable=false) private LocalDateTime expiresAt;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
 @PrePersist void create(){ createdAt=LocalDateTime.now(ZoneOffset.UTC); }
}
