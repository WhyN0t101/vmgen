package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptConflict;
import io.vmgen.api.models.script.ScriptConflictId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for script conflict edges.
 */
@Repository
public interface ScriptConflictRepository extends JpaRepository<ScriptConflict, ScriptConflictId> {

    List<ScriptConflict> findByScriptId(UUID scriptId);

    List<ScriptConflict> findByConflictsWithScriptId(UUID scriptId);

    boolean existsByScriptIdAndConflictsWithScriptId(UUID scriptId, UUID conflictsWithScriptId);

    /** Returns all conflict edges where either endpoint is in the given ID set. */
    @Query("SELECT sc FROM ScriptConflict sc WHERE sc.scriptId IN :ids OR sc.conflictsWithScriptId IN :ids")
    List<ScriptConflict> findAllInvolvingAny(@Param("ids") List<UUID> ids);
}

