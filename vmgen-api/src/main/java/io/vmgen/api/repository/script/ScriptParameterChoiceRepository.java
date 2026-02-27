package io.vmgen.api.repository.script;

import io.vmgen.api.models.script.ScriptParameterChoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for option values of choice-type script parameters.
 */
@Repository
public interface ScriptParameterChoiceRepository extends JpaRepository<ScriptParameterChoice, UUID> {

    List<ScriptParameterChoice> findByParameter_IdOrderByDisplayOrderAsc(UUID parameterId);

    void deleteByParameter_Id(UUID parameterId);
}

