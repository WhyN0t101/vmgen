package io.vmgen.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "template_capabilities")
@IdClass(TemplateCapabilityId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCapability {

    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Id
    @Column(name = "capability", nullable = false, length = 100)
    private String capability;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private Template template;
}
