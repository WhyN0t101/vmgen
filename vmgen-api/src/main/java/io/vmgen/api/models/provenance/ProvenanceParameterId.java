package io.vmgen.api.models.provenance;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProvenanceParameterId implements Serializable {

    private UUID provenanceRecord;
    private String parameterName;
}
