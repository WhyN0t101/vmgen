package io.vmgen.api.dto.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/**
 * Request payload for updating the display order of a category.
 */
@Data
public class CategoryReorderRequest {

    @NotNull
    private UUID categoryId;

    @NotNull
    @Min(0)
    private Integer newOrder;
}

