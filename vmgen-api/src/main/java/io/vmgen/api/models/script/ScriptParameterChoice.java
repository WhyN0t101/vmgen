package io.vmgen.api.models.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * A single choice value for a {@link ScriptParameter} of type {@link ParameterType#CHOICE}.
 */
@Entity
@Table(name = "script_parameter_choices")
@Data
public class ScriptParameterChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    private ScriptParameter parameter;

    @Column(name = "choice_value", nullable = false, length = 255)
    private String choiceValue;

    @Column(name = "choice_label", nullable = false, length = 255)
    private String choiceLabel;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}

