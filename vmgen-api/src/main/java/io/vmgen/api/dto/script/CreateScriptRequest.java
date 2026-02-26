package io.vmgen.api.dto.script;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Request payload for creating a new vulnerability script.
 */
@Data
public class CreateScriptRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Identifier must be lowercase letters, numbers, and hyphens only")
    @Size(max = 255)
    private String identifier;

    @NotBlank
    @Pattern(regexp = "^v\\d+\\.\\d+\\.\\d+$", message = "Version must follow semantic versioning (e.g., v1.0.0)")
    @Size(max = 50)
    private String version;

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

    // Basic requirements – persisted later via script_requirements table
    private List<String> requiredOsFamilies;
    private List<String> requiredCapabilities;
    private Integer minRamMb;
    private Integer minDiskGb;
}

