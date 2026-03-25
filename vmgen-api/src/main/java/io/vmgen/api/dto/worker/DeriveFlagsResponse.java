package io.vmgen.api.dto.worker;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeriveFlagsResponse {
    private List<FlagEntry> flags;
    private String vmLoginPassword;

    @Data
    @Builder
    public static class FlagEntry {
        private String scriptId;
        private String rawFlag;
    }
}
