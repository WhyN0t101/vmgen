package io.vmgen.api.models.build;

import io.vmgen.api.models.vm.VmRequest;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "build_logs")
@Data
public class BuildLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vm_request_id", nullable = false)
    private VmRequest vmRequest;

    @Column(name = "log_level", nullable = false, length = 20)
    private String logLevel;

    @Column(name = "stage", length = 100)
    private String stage;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @PrePersist
    void onCreate() {
        if (loggedAt == null) {
            loggedAt = Instant.now();
        }
    }
}
