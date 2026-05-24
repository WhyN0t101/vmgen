package io.vmgen.api.models.provenance;

import io.vmgen.api.models.vm.VmRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "provenance_records")
@Data
public class ProvenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vm_request_id", nullable = false, unique = true)
    private VmRequest vmRequest;

    @Column(name = "template_identifier", nullable = false, length = 255)
    private String templateIdentifier;

    @Column(name = "template_version", nullable = false, length = 50)
    private String templateVersion;

    @Column(name = "template_sha256", nullable = false, length = 64)
    private String templateSha256;

    @Column(name = "build_seed", nullable = false, length = 64)
    private String buildSeed;

    @Column(name = "flag_derivation_timestamp", nullable = false)
    private Instant flagDerivationTimestamp;

    @Column(name = "difficulty_level", length = 50)
    private String difficultyLevel;

    @Column(name = "telemetry_enabled", nullable = false)
    private Boolean telemetryEnabled;

    @Column(name = "ova_sha256", nullable = false, length = 64)
    private String ovaSha256;

    @Column(name = "finalised_at", nullable = false)
    private Instant finalisedAt;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "build_started_at")
    private Instant buildStartedAt;

    @Column(name = "build_finished_at")
    private Instant buildFinishedAt;

    @Column(name = "vm_login_password", length = 64)
    private String vmLoginPassword;

    @OneToMany(mappedBy = "provenanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("executionOrder ASC")
    private List<ProvenanceScript> scripts = new ArrayList<>();

    @OneToMany(mappedBy = "provenanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProvenanceParameter> parameters = new ArrayList<>();

    @OneToMany(mappedBy = "provenanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProvenanceDependency> dependencies = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (finalisedAt == null) {
            finalisedAt = Instant.now();
        }
    }
}
