package io.vmgen.api.repository;

import io.vmgen.api.models.template.ScriptCompatibility;
import io.vmgen.api.models.template.ScriptCompatibilityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScriptCompatibilityRepository
        extends JpaRepository<ScriptCompatibility, ScriptCompatibilityId> {

    List<ScriptCompatibility> findAllByTemplateId(UUID templateId);

    boolean existsByScriptIdAndTemplateId(UUID scriptId, UUID templateId);

    void deleteByScriptIdAndTemplateId(UUID scriptId, UUID templateId);
}
