package io.vmgen.api.models.flag;

import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.vm.VmRequest;
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
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "flags")
@Data
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vm_request_id", nullable = false)
    private VmRequest vmRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Column(name = "flag_value_hash", nullable = false, length = 64)
    private String flagValueHash;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "flag_derivation_timestamp", nullable = false)
    private Instant flagDerivationTimestamp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (keyVersion == null) {
            keyVersion = 1;
        }
    }
}
