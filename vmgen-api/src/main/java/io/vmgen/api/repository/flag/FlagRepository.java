package io.vmgen.api.repository.flag;

import io.vmgen.api.models.flag.Flag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlagRepository extends JpaRepository<Flag, UUID> {
    List<Flag> findByVmRequest_Id(UUID vmRequestId);
    Optional<Flag> findByVmRequest_IdAndScript_Id(UUID vmRequestId, UUID scriptId);
}