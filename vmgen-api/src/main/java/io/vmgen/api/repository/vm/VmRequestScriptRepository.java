package io.vmgen.api.repository.vm;

import io.vmgen.api.models.vm.VmRequestScript;
import io.vmgen.api.models.vm.VmRequestScriptId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VmRequestScriptRepository extends JpaRepository<VmRequestScript, VmRequestScriptId> {
}
