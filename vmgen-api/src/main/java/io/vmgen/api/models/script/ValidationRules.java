package io.vmgen.api.models.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Validation rules for a script parameter.
 *
 * <p>This structure is persisted as JSONB via {@link ValidationRulesConverter}
 * so that the rules can evolve without requiring schema changes.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRules {

    private String pattern;
    private Integer min;
    private Integer max;
    private Integer minLength;
    private Integer maxLength;
}

