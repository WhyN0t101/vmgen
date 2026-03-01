package io.vmgen.api.repository.template;

import io.vmgen.api.models.template.Template;
import io.vmgen.api.models.template.TemplateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {

    Optional<Template> findByIdentifierAndVersion(String identifier, String version);

    List<Template> findAllByStatusOrderByCreatedAtDesc(TemplateStatus status);

    Page<Template> findAllByStatus(TemplateStatus status, Pageable pageable);

    /**
     * Paginated active-template query with optional full-text and OS-family filters.
     * Pass {@code null} for either filter to skip it.
     * {@code searchPattern} must already include SQL wildcards (e.g. {@code "%ubuntu%"}).
     */
    @Query("""
            SELECT t FROM Template t
            WHERE t.status = :status
            AND (:searchPattern IS NULL
                 OR LOWER(t.identifier) LIKE :searchPattern
                 OR LOWER(t.version) LIKE :searchPattern
                 OR (t.description IS NOT NULL AND LOWER(t.description) LIKE :searchPattern))
            AND (:osFamily IS NULL OR LOWER(t.osFamily) = :osFamily)
            """)
    Page<Template> findActiveFiltered(
            @Param("status") TemplateStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("osFamily") String osFamily,
            Pageable pageable);

    List<Template> findAllByOrderByCreatedAtDesc();

    /**
     * Checks whether a template already exists for the given Proxmox VMID and node.
     */
    boolean existsByProxmoxVmidAndProxmoxNode(Integer proxmoxVmid, String proxmoxNode);
}