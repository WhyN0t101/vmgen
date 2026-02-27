package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptParameter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for parameter definitions attached to scripts.
 */
@Repository
public interface ScriptParameterRepository extends JpaRepository<ScriptParameter, UUID> {

    List<ScriptParameter> findByScript_IdOrderByDisplayOrderAsc(UUID scriptId);

    boolean existsByScript_IdAndParameterName(UUID scriptId, String parameterName);
}

