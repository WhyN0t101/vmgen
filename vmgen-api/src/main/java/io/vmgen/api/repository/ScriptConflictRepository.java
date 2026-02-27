package io.vmgen.api.repository;

import io.vmgen.api.models.script.ScriptConflict;
import io.vmgen.api.models.script.ScriptConflictId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for script conflict edges.
 */
@Repository
public interface ScriptConflictRepository extends JpaRepository<ScriptConflict, ScriptConflictId> {

    List<ScriptConflict> findByScriptId(UUID scriptId);

    List<ScriptConflict> findByConflictsWithScriptId(UUID scriptId);

    boolean existsByScriptIdAndConflictsWithScriptId(UUID scriptId, UUID conflictsWithScriptId);
}

