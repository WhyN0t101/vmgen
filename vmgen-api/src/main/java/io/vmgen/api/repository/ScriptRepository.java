package io.vmgen.api.repository;

import io.vmgen.api.models.script.Script;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID>, JpaSpecificationExecutor<Script> {

    Optional<Script> findByIdentifierAndVersion(String identifier, String version);

    boolean existsByIdentifierAndVersion(String identifier, String version);
}

