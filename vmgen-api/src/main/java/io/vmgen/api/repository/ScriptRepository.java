package io.vmgen.api.repository;

import io.vmgen.api.models.script.Script;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID>, JpaSpecificationExecutor<Script> {

    Optional<Script> findByIdentifierAndVersion(String identifier, String version);

    boolean existsByIdentifierAndVersion(String identifier, String version);

    @Query("SELECT s FROM Script s LEFT JOIN FETCH s.category WHERE s.id IN :ids AND s.isActive = true")
    List<Script> findActiveByIdsWithCategory(@Param("ids") List<UUID> ids);

    @Query("SELECT s FROM Script s LEFT JOIN FETCH s.category WHERE s.isActive = true ORDER BY s.name")
    List<Script> findAllActiveWithCategory();
}

