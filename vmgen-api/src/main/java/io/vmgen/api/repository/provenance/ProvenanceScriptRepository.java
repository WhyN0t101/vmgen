package io.vmgen.api.repository.provenance;

import io.vmgen.api.models.provenance.ProvenanceScript;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvenanceScriptRepository extends JpaRepository<ProvenanceScript, UUID> {
}
