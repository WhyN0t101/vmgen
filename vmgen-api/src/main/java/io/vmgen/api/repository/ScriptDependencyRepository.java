package io.vmgen.api.repository;

import io.vmgen.api.models.script.ScriptDependency;
import io.vmgen.api.models.script.ScriptDependencyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for script dependency edges.
 */
@Repository
public interface ScriptDependencyRepository extends JpaRepository<ScriptDependency, ScriptDependencyId> {

    List<ScriptDependency> findByScriptId(UUID scriptId);

    boolean existsByScriptIdAndRequiresScriptId(UUID scriptId, UUID requiresScriptId);
}

