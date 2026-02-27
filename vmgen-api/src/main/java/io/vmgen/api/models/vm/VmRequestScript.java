package io.vmgen.api.models.vm;

import io.vmgen.api.models.script.Script;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;

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
