package io.vmgen.api.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scripts")
@Data
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "vulnerability_category", nullable = false, length = 100)
    private String vulnerabilityCategory;

    @Column(name = "difficulty_level", nullable = false, length = 50)
    private String difficultyLevel;

    @Column(name = "script_content", nullable = false, columnDefinition = "TEXT")
    private String scriptContent;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "flag_location", nullable = false, columnDefinition = "TEXT")
    private String flagLocation;

    @Column(name = "flag_ownership", length = 100)
    private String flagOwnership;

    @Column(name = "flag_permissions", length = 10)
    private String flagPermissions;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
