package io.vmgen.api.models.provenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

@Entity
@Table(name = "provenance_dependencies")
@Data
public class ProvenanceDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provenance_id", nullable = false)
    private ProvenanceRecord provenanceRecord;

    @Column(name = "dependency_type", nullable = false, length = 50)
    private String dependencyType;

    @Column(name = "dependency_name", nullable = false, length = 255)
    private String dependencyName;

    @Column(name = "dependency_version", length = 100)
    private String dependencyVersion;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @PrePersist
    void onCreate() {
        if (retrievedAt == null) {
            retrievedAt = Instant.now();
        }
    }
}
