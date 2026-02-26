package io.vmgen.api.models.vm;

import io.vmgen.api.models.flag.Flag;
import io.vmgen.api.models.provenance.ProvenanceRecord;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.build.BuildLog;
import io.vmgen.api.models.template.Template;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vm_requests")
@Getter
@Setter
public class VmRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "build_seed", nullable = false, length = 64)
    private String buildSeed;

    @Column(name = "difficulty_override", length = 50)
    private String difficultyOverride;

    @Column(name = "telemetry_enabled", nullable = false)
    private Boolean telemetryEnabled = false;
    
    @OneToMany(mappedBy = "vmRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("executionOrder ASC")
    private List<VmRequestScript> requestScripts = new ArrayList<>();
    
    @OneToMany(mappedBy = "vmRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Flag> flags = new ArrayList<>();
    
    @OneToOne(mappedBy = "vmRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ProvenanceRecord provenance;
    
    @OneToMany(mappedBy = "vmRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("loggedAt ASC")
    private List<BuildLog> buildLogs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private VmRequestState state = VmRequestState.QUEUED;

    // Artifact fields
    @Column(name = "ova_filename")
    private String ovaFilename;

    @Column(name = "ova_sha256", length = 64)
    private String ovaSha256;

    @Column(name = "ova_size_bytes")
    private Long ovaSizeBytes;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "storage_deleted_at")
    private Instant storageDeletedAt;

    // Progress tracking
    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    // Timestamps
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "building_started_at")
    private Instant buildingStartedAt;

    @Column(name = "exporting_started_at")
    private Instant exportingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // Worker tracking (V4 columns)
    @Column(name = "claimed_by_worker_id", length = 255)
    private String claimedByWorkerId;

    @Column(name = "current_worker_stage", length = 100)
    private String currentWorkerStage;

    @Column(name = "worker_last_heartbeat_at")
    private Instant workerLastHeartbeatAt;

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
        if (state == null) {
            state = VmRequestState.QUEUED;
        }
        if (telemetryEnabled == null) {
            telemetryEnabled = false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VmRequest)) return false;
        VmRequest that = (VmRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
