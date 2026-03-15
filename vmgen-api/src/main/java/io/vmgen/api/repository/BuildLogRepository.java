package io.vmgen.api.repository;

import io.vmgen.api.models.build.BuildLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildLogRepository extends JpaRepository<BuildLog, Long> {
}
