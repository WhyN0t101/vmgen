package io.vmgen.api.repository.telemetry;

import io.vmgen.api.models.telemetry.TelemetryUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryUpload, UUID> {
    Optional<TelemetryUpload> findByVmRequest_IdAndDeletedAtIsNull(UUID vmRequestId);

    @Query("SELECT t FROM TelemetryUpload t WHERE t.deletedAt IS NULL AND t.uploadedAt < :cutoff")
    List<TelemetryUpload> findActiveUploadedBefore(@Param("cutoff") Instant cutoff);
}
