package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptCapability;
import io.vmgen.api.models.script.ScriptCapabilityId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScriptCapabilityRepository extends JpaRepository<ScriptCapability, ScriptCapabilityId> {

    List<ScriptCapability> findByScriptId(UUID scriptId);

    List<ScriptCapability> findByScriptIdIn(Collection<UUID> scriptIds);

    void deleteByScriptId(UUID scriptId);
}
