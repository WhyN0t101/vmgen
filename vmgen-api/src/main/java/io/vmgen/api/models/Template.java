package io.vmgen.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "templates")
@Data
public class Template {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "os_family", nullable = false, length = 100)
    private String osFamily;

    @Column(name = "os_version", nullable = false, length = 100)
    private String osVersion;

    @Column(name = "architecture", nullable = false, length = 50)
    private String architecture;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    @Column(name = "disk_size_gb", nullable = false)
    private Integer diskSizeGb;

    @Column(name = "min_ram_mb", nullable = false)
    private Integer minRamMb = 2048;

    @Column(name = "min_cpu_cores", nullable = false)
    private Integer minCpuCores = 2;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(name = "validation_notes", columnDefinition = "TEXT")
    private String validationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    // V3 fields
    @Column(name = "status", nullable = false, length = 50)
    private String status = "draft";

    @Column(name = "proxmox_vmid")
    private Integer proxmoxVmid;

    @Column(name = "proxmox_node", length = 100)
    private String proxmoxNode;

    @Column(name = "disk_storage", length = 100)
    private String diskStorage;

    @Column(name = "validation_logs", columnDefinition = "jsonb")
    @ColumnTransformer(
        read = "validation_logs::text",
        write = "?::jsonb"
    )
    private String validationLogs; // Stored as JSONB, handled as String in Java

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "expected_ssh_user", length = 50)
    private String expectedSshUser = "root";

    @Column(name = "expected_ssh_port")
    private Integer expectedSshPort = 22;

    @Column(name = "network_bridge", length = 100)
    private String networkBridge;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY, orphanRemoval = true)
    private List<TemplateCapability> capabilities = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "draft";
        }
        if (minRamMb == null) {
            minRamMb = 2048;
        }
        if (minCpuCores == null) {
            minCpuCores = 2;
        }
        if (expectedSshUser == null) {
            expectedSshUser = "root";
        }
        if (expectedSshPort == null) {
            expectedSshPort = 22;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
