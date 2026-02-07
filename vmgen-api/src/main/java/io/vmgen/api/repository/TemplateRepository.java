package io.vmgen.api.repository;

import io.vmgen.api.models.Template;
import io.vmgen.api.models.TemplateStatus;
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
}