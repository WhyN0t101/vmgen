package io.vmgen.api.repository.template;

import io.vmgen.api.models.template.Template;
import io.vmgen.api.models.template.TemplateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {

    Optional<Template> findByIdentifierAndVersion(String identifier, String version);

    List<Template> findAllByStatusOrderByCreatedAtDesc(TemplateStatus status);

    List<Template> findAllByOrderByCreatedAtDesc();

    /**
     * Checks whether a template already exists for the given Proxmox VMID and node.
     */
    boolean existsByProxmoxVmidAndProxmoxNode(Integer proxmoxVmid, String proxmoxNode);
}