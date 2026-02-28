package io.vmgen.api.models.script;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Validation rules for a script parameter.
 *
 * <p>This structure is persisted as JSONB via {@link ValidationRulesConverter}
 * so that the rules can evolve without requiring schema changes.</p>
 *
 * <p>NON_NULL ensures that unset rules are never written to JSONB or returned
 * in API responses, which prevents the frontend from receiving {@code null}
 * values and triggering false validation errors.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationRules {

    private String pattern;
    private Integer min;
    private Integer max;
    private Integer minLength;
    private Integer maxLength;
}

