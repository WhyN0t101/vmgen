package io.vmgen.api.models.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for automatic random value generation of a script parameter.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RandomConfig {

    // For string generation
    private Integer length;
    private String charset; // 'alphanumeric', 'alphanumeric_special', 'hex', 'numeric'

    // For integer generation
    private Integer min;
    private Integer max;
}

