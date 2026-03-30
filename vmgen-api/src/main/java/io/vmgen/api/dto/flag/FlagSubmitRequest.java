package io.vmgen.api.dto.flag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FlagSubmitRequest {

    @NotBlank
    @Size(max = 64)
    private String flagValue;
}
