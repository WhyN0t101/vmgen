package io.vmgen.api.models.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Parameter definition attached to a vulnerability script.
 *
 * <p>This describes what the parameter is called, how it is validated and
 * whether it is user-provided or generated automatically.</p>
 */
@Entity
@Table(name = "script_parameters")
@Data
public class ScriptParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Pattern(regexp = "^[A-Z_]+$", message = "Parameter name must be uppercase with underscores")
    @Column(name = "parameter_name", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "parameter_type", nullable = false, length = 20)
    private ParameterType parameterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_source", nullable = false, length = 30)
    private ValueSource valueSource = ValueSource.USER_INPUT;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = Boolean.TRUE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules")
    private ValidationRules validationRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "random_config")
    private RandomConfig randomConfig;

    @Column(name = "user_can_override", nullable = false)
    private Boolean userCanOverride = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "parameter", orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ScriptParameterChoice> choices = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (isRequired == null) {
            isRequired = Boolean.TRUE;
        }
        if (userCanOverride == null) {
            userCanOverride = Boolean.FALSE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

