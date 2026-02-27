package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptDependency;
import io.vmgen.api.models.script.ScriptDependencyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for script dependency edges.
 */
@Repository
public interface ScriptDependencyRepository extends JpaRepository<ScriptDependency, ScriptDependencyId> {

    List<ScriptDependency> findByScriptId(UUID scriptId);

    boolean existsByScriptIdAndRequiresScriptId(UUID scriptId, UUID requiresScriptId);

    /** Returns all dependency edges where the dependent script is in the given ID set. */
    @Query("SELECT sd FROM ScriptDependency sd WHERE sd.scriptId IN :ids")
    List<ScriptDependency> findAllByScriptIdIn(@Param("ids") List<UUID> ids);
}

