package io.vmgen.api.repository.vm;

import io.vmgen.api.models.vm.VmRequestScriptParameter;
import io.vmgen.api.models.vm.VmRequestScriptParameterId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for concrete script parameter values on a VM request.
 */
@Repository
public interface VmRequestScriptParameterRepository
        extends JpaRepository<VmRequestScriptParameter, VmRequestScriptParameterId> {

    List<VmRequestScriptParameter> findByVmRequest_Id(UUID vmRequestId);
}

