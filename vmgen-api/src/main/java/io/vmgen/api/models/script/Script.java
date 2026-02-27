package io.vmgen.api.models.script;

import io.vmgen.api.models.user.User;
import io.vmgen.api.models.vulnerability.VulnerabilityCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private VulnerabilityCategory category;

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

    @OneToMany(mappedBy = "script", orphanRemoval = true)
    private List<ScriptConflict> conflicts = new ArrayList<>();

    @OneToMany(mappedBy = "script", orphanRemoval = true)
    private List<ScriptDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "script", orphanRemoval = true)
    private List<ScriptParameter> parameters = new ArrayList<>();

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
