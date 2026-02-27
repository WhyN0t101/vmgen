package io.vmgen.api.models.template;

import io.vmgen.api.models.script.Script;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "script_compatibility")
@IdClass(ScriptCompatibilityId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCompatibility {

    @Id
    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private Template template;
}
