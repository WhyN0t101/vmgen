package io.vmgen.api.repository;

import io.vmgen.api.models.build.BuildLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BuildLogRepository extends JpaRepository<BuildLog, Long> {
    List<BuildLog> findByVmRequest_IdOrderByLoggedAtAsc(UUID vmRequestId);
}
