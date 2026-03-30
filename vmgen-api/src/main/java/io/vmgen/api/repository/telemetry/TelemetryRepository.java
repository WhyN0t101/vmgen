package io.vmgen.api.repository.telemetry;

import io.vmgen.api.models.telemetry.TelemetryUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryUpload, UUID> {
    Optional<TelemetryUpload> findByVmRequest_IdAndDeletedAtIsNull(UUID vmRequestId);
}
