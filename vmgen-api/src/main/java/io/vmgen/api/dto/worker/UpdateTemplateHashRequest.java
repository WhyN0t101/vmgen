package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTemplateHashRequest {

    @NotBlank
    @Size(min = 64, max = 64)
    @Pattern(regexp = "[0-9a-f]{64}", message = "must be a lowercase hex SHA-256 digest")
    private String sha256;
}
