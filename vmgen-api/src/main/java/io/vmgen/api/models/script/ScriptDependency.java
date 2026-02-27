package io.vmgen.api.models.script;

import io.vmgen.api.models.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Directed dependency edge between two vulnerability scripts.
 *
 * <p>If script A "requires" script B, then B must be present whenever A is
 * selected, and B must execute before A. The composite primary key prevents
 * duplicate rows for the same pair.</p>
 */
@Entity
@Table(name = "script_dependencies")
@IdClass(ScriptDependencyId.class)
@Data
public class ScriptDependency {

    @Id
    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Id
    @Column(name = "requires_script_id", nullable = false)
    private UUID requiresScriptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requires_script_id", insertable = false, updatable = false)
    private Script requires;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

