package io.vmgen.api.models.provenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "provenance_scripts")
@Data
public class ProvenanceScript {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provenance_id", nullable = false)
    private ProvenanceRecord provenanceRecord;

    @Column(name = "script_id")
    private UUID scriptId;

    @Column(name = "script_identifier", nullable = false, length = 255)
    private String scriptIdentifier;

    @Column(name = "script_version", nullable = false, length = 50)
    private String scriptVersion;

    @Column(name = "script_sha256", nullable = false, length = 64)
    private String scriptSha256;

    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;
}
