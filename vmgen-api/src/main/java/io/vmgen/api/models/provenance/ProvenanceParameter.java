package io.vmgen.api.models.provenance;

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
@Table(name = "provenance_parameters")
@IdClass(ProvenanceParameterId.class)
@Data
public class ProvenanceParameter {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provenance_id", nullable = false)
    private ProvenanceRecord provenanceRecord;

    @Id
    @Column(name = "parameter_name", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "parameter_value", nullable = false, columnDefinition = "TEXT")
    private String parameterValue;
}
