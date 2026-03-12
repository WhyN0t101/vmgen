package io.vmgen.api.repository.provenance;

import io.vmgen.api.models.provenance.ProvenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvenanceRecordRepository extends JpaRepository<ProvenanceRecord, UUID> {
    Optional<ProvenanceRecord> findByVmRequest_Id(UUID vmRequestId);
}