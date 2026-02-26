package io.vmgen.api.models.provenance;

import io.vmgen.api.models.vm.VmRequest;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

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

    @PrePersist
    void onCreate() {
        if (finalisedAt == null) {
            finalisedAt = Instant.now();
        }
    }
}
