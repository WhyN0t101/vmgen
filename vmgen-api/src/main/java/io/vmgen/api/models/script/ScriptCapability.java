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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "script_capabilities")
@IdClass(ScriptCapabilityId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCapability {

    @Id
    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Id
    @Column(name = "capability", nullable = false, length = 100)
    private String capability;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;
}
