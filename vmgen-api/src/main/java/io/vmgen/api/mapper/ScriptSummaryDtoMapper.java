package io.vmgen.api.mapper;

import io.vmgen.api.models.script.Script;


public final class ScriptSummaryDtoMapper {

    private ScriptSummaryDtoMapper() {
        // Utility class
    }

    /**
     * Maps a {@link Script} to the script module's {@link io.vmgen.api.dto.script.ScriptSummaryDto}.
     */
    public static io.vmgen.api.dto.script.ScriptSummaryDto toScriptSummary(Script script) {
        if (script == null) {
            return null;
        }
        return io.vmgen.api.dto.script.ScriptSummaryDto.builder()
                .id(script.getId())
                .identifier(script.getIdentifier())
                .version(script.getVersion())
                .name(script.getName())
                .build();
    }

    /**
     * Maps a {@link Script} to the category module's {@link io.vmgen.api.dto.category.ScriptSummaryDto}.
     */
    public static io.vmgen.api.dto.category.ScriptSummaryDto toCategorySummary(Script script) {
        if (script == null) {
            return null;
        }
        return io.vmgen.api.dto.category.ScriptSummaryDto.builder()
                .id(script.getId())
                .identifier(script.getIdentifier())
                .version(script.getVersion())
                .name(script.getName())
                .difficultyLevel(script.getDifficultyLevel())
                .isActive(script.getIsActive())
                .build();
    }
}

