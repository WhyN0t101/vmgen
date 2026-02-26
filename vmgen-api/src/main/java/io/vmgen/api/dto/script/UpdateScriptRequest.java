package io.vmgen.api.dto.script;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Request payload for updating an existing vulnerability script.
 * Identifier and version remain immutable once created.
 */
@Data
public class UpdateScriptRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotNull
    private UUID categoryId;

    @NotBlank
    @Size(max = 50)
    private String difficultyLevel;

    @NotBlank
    private String scriptContent;

    @NotBlank
    private String flagLocation;

    @Size(max = 100)
    private String flagOwnership;

    @Size(max = 10)
    @Pattern(regexp = "^[0-7]{3,4}$", message = "Flag permissions must be octal (e.g., 644 or 0644)")
    private String flagPermissions;

    private List<String> requiredOsFamilies;
    private List<String> requiredCapabilities;
    private Integer minRamMb;
    private Integer minDiskGb;
}

