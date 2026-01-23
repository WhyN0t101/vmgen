package io.vmgen.api.repository;

import io.vmgen.api.models.Template;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {
    
    List<Template> findByStatus(String status);
    
    Optional<Template> findByIdentifierAndVersion(String identifier, String version);
    
    List<Template> findAllByStatusOrderByCreatedAtDesc(String status);
    
    List<Template> findAllByOrderByCreatedAtDesc();
}
