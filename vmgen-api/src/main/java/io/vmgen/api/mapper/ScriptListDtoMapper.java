package io.vmgen.api.mapper;

import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.vulnerability.VulnerabilityCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public final class ScriptListDtoMapper {

    private ScriptListDtoMapper() {
        // Utility class
    }

    /**
     * Maps a {@link Script} to a {@link ScriptListDto} without conflict/dependency metadata.
     * The conflict/dependency lists are initialised to empty lists.
     */
    public static ScriptListDto toBase(Script script) {
        return baseBuilder(script)
                .conflictsWithIds(List.of())
                .requiresIds(List.of())
                .build();
    }

    /**
     * Maps a {@link Script} to a {@link ScriptListDto} enriched with conflict and dependency IDs.
     */
    public static ScriptListDto toEnriched(
            Script script,
            Map<UUID, List<UUID>> conflictMap,
            Map<UUID, List<UUID>> requiresMap
    ) {
        return baseBuilder(script)
                .conflictsWithIds(conflictMap.getOrDefault(script.getId(), List.of()))
                .requiresIds(requiresMap.getOrDefault(script.getId(), List.of()))
                .build();
    }

    private static ScriptListDto.ScriptListDtoBuilder baseBuilder(Script script) {
        VulnerabilityCategory category = script.getCategory();
        return ScriptListDto.builder()
                .id(script.getId())
                .identifier(script.getIdentifier())
                .version(script.getVersion())
                .name(script.getName())
                .description(script.getDescription())
                .difficultyLevel(script.getDifficultyLevel())
                .isActive(script.getIsActive())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryColor(category != null ? category.getColor() : null)
                .owaspMapping(category != null ? category.getOwaspMapping() : null)
                .createdAt(script.getCreatedAt());
    }

}

