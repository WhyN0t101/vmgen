package io.vmgen.api.models;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "templates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"identifier", "version"})
})
@Getter
@Setter
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
    private Integer minRamMb;

    @Column(name = "min_cpu_cores", nullable = false)
    private Integer minCpuCores;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TemplateStatus status;

    @Column(name = "proxmox_vmid", nullable = false)
    private Integer proxmoxVmid;

    @Column(name = "proxmox_node", nullable = false, length = 100)
    private String proxmoxNode;

    @Column(name = "disk_storage", length = 100)
    private String diskStorage;

    @Column(name = "validation_logs", columnDefinition = "jsonb")
    @ColumnTransformer(
            read = "validation_logs::text",
            write = "?::jsonb"
    )
    private String validationLogs;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "expected_ssh_user", nullable = false, length = 50)
    private String expectedSshUser;

    @Column(name = "expected_ssh_port", nullable = false)
    private Integer expectedSshPort;

    @Column(name = "network_bridge", length = 100)
    private String networkBridge;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TemplateCapability> capabilities = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // Bidirectional relationship helper
    public void addCapability(TemplateCapability capability) {
        capabilities.add(capability);
        capability.setTemplate(this);
    }

    public void removeCapability(TemplateCapability capability) {
        capabilities.remove(capability);
        capability.setTemplate(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Template)) return false;
        Template template = (Template) o;
        return id != null && Objects.equals(id, template.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}