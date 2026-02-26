package io.vmgen.api.models.vm;

import io.vmgen.api.models.script.Script;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "vm_request_scripts")
@Data
@IdClass(VmRequestScriptId.class)
public class VmRequestScript {

    @Id
    @Column(name = "vm_request_id")
    private UUID vmRequestId;

    @Id
    @Column(name = "script_id")
    private UUID scriptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vm_request_id", insertable = false, updatable = false)
    private VmRequest vmRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", insertable = false, updatable = false)
    private Script script;

    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder;
}
