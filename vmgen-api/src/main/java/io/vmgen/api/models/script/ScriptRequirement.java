package io.vmgen.api.models.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "script_requirements")
@IdClass(ScriptRequirementId.class)
@Data
public class ScriptRequirement {

    public static final String TYPE_OS_FAMILY = "OS_FAMILY";

    @Id
    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Id
    @Column(name = "requirement_type", nullable = false, length = 50)
    private String requirementType;

    @Id
    @Column(name = "requirement_value", nullable = false, columnDefinition = "TEXT")
    private String requirementValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;
}
