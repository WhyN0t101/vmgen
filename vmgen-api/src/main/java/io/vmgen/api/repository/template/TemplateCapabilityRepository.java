package io.vmgen.api.repository.template;

import io.vmgen.api.models.template.TemplateCapability;
import io.vmgen.api.models.template.TemplateCapabilityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateCapabilityRepository extends JpaRepository<TemplateCapability, TemplateCapabilityId> {
    
    List<TemplateCapability> findAllByTemplateId(UUID templateId);
    
    void deleteAllByTemplateId(UUID templateId);
}
