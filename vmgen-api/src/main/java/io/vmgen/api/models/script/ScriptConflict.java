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
 * Bidirectional conflict edge between two vulnerability scripts.
 *
 * <p>The database enforces {@code script_id != conflicts_with_script_id} via a CHECK
 * constraint and uses a composite primary key across both columns. At the JPA
 * level we keep the UUIDs as id fields and expose the associated {@link Script}
 * entities for navigation.</p>
 */
@Entity
@Table(name = "script_conflicts")
@IdClass(ScriptConflictId.class)
@Data
public class ScriptConflict {

    @Id
    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Id
    @Column(name = "conflicts_with_script_id", nullable = false)
    private UUID conflictsWithScriptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conflicts_with_script_id", insertable = false, updatable = false)
    private Script conflictsWith;

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

