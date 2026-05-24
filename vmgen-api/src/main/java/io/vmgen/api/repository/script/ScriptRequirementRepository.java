package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptRequirement;
import io.vmgen.api.models.script.ScriptRequirementId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScriptRequirementRepository extends JpaRepository<ScriptRequirement, ScriptRequirementId> {

    List<ScriptRequirement> findByScriptId(UUID scriptId);

    List<ScriptRequirement> findByScriptIdIn(Collection<UUID> scriptIds);

    void deleteByScriptId(UUID scriptId);
}
