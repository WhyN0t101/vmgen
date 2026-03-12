package io.vmgen.api.repository.vm;

import io.vmgen.api.models.vm.VmRequestScript;
import io.vmgen.api.models.vm.VmRequestScriptId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VmRequestScriptRepository extends JpaRepository<VmRequestScript, VmRequestScriptId> {

    @Query("SELECT vrs FROM VmRequestScript vrs JOIN FETCH vrs.script WHERE vrs.vmRequestId = :vmRequestId ORDER BY vrs.executionOrder ASC")
    List<VmRequestScript> findWithScriptByVmRequestId(@Param("vmRequestId") UUID vmRequestId);
}
