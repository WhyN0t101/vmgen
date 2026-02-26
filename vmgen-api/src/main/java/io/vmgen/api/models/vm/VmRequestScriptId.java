package io.vmgen.api.models.vm;

import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

@Data
public class VmRequestScriptId implements Serializable {
    private UUID vmRequestId;
    private UUID scriptId;
}
