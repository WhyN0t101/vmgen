package io.vmgen.api.service.script;

import io.vmgen.api.dto.script.ScriptCompatibilityInfo;
import io.vmgen.api.dto.script.ScriptCompatibilityViewDto;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.script.ScriptSelectionResult;
import io.vmgen.api.dto.script.ScriptSummaryDto;
import io.vmgen.api.exceptions.CircularDependencyException;
import io.vmgen.api.exceptions.ScriptConflictException;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.script.ScriptConflict;
import io.vmgen.api.models.script.ScriptConflictId;
import io.vmgen.api.models.script.ScriptDependency;
import io.vmgen.api.models.script.ScriptDependencyId;
import io.vmgen.api.models.user.User;
import io.vmgen.api.repository.script.ScriptConflictRepository;
import io.vmgen.api.repository.script.ScriptDependencyRepository;
import io.vmgen.api.repository.script.ScriptRepository;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.mapper.ScriptListDtoMapper;
import io.vmgen.api.mapper.ScriptSummaryDtoMapper;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptCompatibilityService {

    private final ScriptRepository scriptRepository;
    private final ScriptConflictRepository conflictRepository;
    private final ScriptDependencyRepository dependencyRepository;
    private final UserRepository userRepository;

    /**
     * Validates the given script IDs against configured conflicts and
     * dependencies. Returns a {@link ScriptSelectionResult} containing
     * the ordered execution list and any auto-added scripts.
     */
    @Transactional
    public ScriptSelectionResult resolveScriptSelection(List<UUID> selectedScriptIds) {
        if (selectedScriptIds == null || selectedScriptIds.isEmpty()) {
            throw new ScriptConflictException("At least one script must be selected");
        }

        // 1) Detect direct conflicts.
        List<String> conflicts = detectConflicts(selectedScriptIds);
        if (!conflicts.isEmpty()) {
            throw new ScriptConflictException("Script conflicts detected", conflicts);
        }

        // 2) Resolve dependencies and compute the full set of scripts.
        Set<UUID> resolved = new LinkedHashSet<>(selectedScriptIds);
        List<UUID> autoAdded = resolveDependencies(resolved);

        // 3) Derive execution order using a dependency-aware topological sort.
        List<UUID> ordered = topologicalSort(resolved);

        log.debug("Resolved {} selected scripts into {} scripts ({} auto-added)",
                selectedScriptIds.size(), resolved.size(), autoAdded.size());

        return ScriptSelectionResult.builder()
                .orderedScriptIds(ordered)
                .autoAddedScriptIds(autoAdded)
                .build();
    }

    /**
     * Returns all active scripts enriched with their conflict and dependency IDs.
     * Used by admin UIs that must enforce compatibility constraints at selection time
     * (e.g. the template registration form when picking compatible scripts).
     */
    @Transactional(readOnly = true)
    public List<ScriptListDto> getAllActiveEnriched() {
        List<Script> scripts = scriptRepository.findAllActiveWithCategory();
        return enrichScripts(scripts);
    }

    /**
     * Returns the given scripts enriched with their conflict and dependency IDs.
     * The conflict/dependency maps are scoped to the provided script set.
     */
    @Transactional(readOnly = true)
    public List<ScriptListDto> getEnrichedForScripts(List<Script> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }
        return enrichScripts(scripts);
    }

    private List<ScriptListDto> enrichScripts(List<Script> scripts) {
        List<UUID> allIds = scripts.stream()
                .map(Script::getId)
                .toList();

        Set<UUID> idSet = new HashSet<>(allIds);

        Map<UUID, List<UUID>> conflictMap = new HashMap<>();
        for (ScriptConflict edge : conflictRepository.findAllInvolvingAny(allIds)) {
            UUID a = edge.getScriptId();
            UUID b = edge.getConflictsWithScriptId();
            if (idSet.contains(a) && idSet.contains(b)) {
                conflictMap.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                conflictMap.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            }
        }

        Map<UUID, List<UUID>> requiresMap = new HashMap<>();
        for (ScriptDependency dep : dependencyRepository.findAllByScriptIdIn(allIds)) {
            requiresMap.computeIfAbsent(dep.getScriptId(), k -> new ArrayList<>())
                    .add(dep.getRequiresScriptId());
        }

        return scripts.stream()
                .map(s -> toEnrichedDto(s, conflictMap, requiresMap))
                .toList();
    }

    private ScriptListDto toEnrichedDto(Script script,
                                        Map<UUID, List<UUID>> conflictMap,
                                        Map<UUID, List<UUID>> requiresMap) {
        return ScriptListDtoMapper.toEnriched(script, conflictMap, requiresMap);
    }

    /**
     * Returns raw compatibility information for a given script.
     */
    @Transactional(readOnly = true)
    public ScriptCompatibilityInfo getCompatibilityInfo(UUID scriptId) {
        List<UUID> conflictsWith = new ArrayList<>();

        conflictRepository.findByScriptId(scriptId)
                .forEach(c -> conflictsWith.add(c.getConflictsWithScriptId()));

        conflictRepository.findByConflictsWithScriptId(scriptId)
                .forEach(c -> conflictsWith.add(c.getScriptId()));

        List<UUID> requires = dependencyRepository.findByScriptId(scriptId).stream()
                .map(ScriptDependency::getRequiresScriptId)
                .toList();

        return ScriptCompatibilityInfo.builder()
                .scriptId(scriptId)
                .conflictsWith(conflictsWith)
                .requires(requires)
                .build();
    }

    /**
     * Creates a bidirectional conflict edge between the two scripts.
     */
    @Transactional
    public void addConflict(UUID scriptId, UUID conflictsWithId, UUID adminId) {
        if (scriptId.equals(conflictsWithId)) {
            throw new ScriptConflictException("A script cannot conflict with itself");
        }

        // A script cannot simultaneously require and conflict with the same script.
        if (dependencyRepository.existsByScriptIdAndRequiresScriptId(scriptId, conflictsWithId)
                || dependencyRepository.existsByScriptIdAndRequiresScriptId(conflictsWithId, scriptId)) {
            throw new ScriptConflictException("A script cannot be both a dependency and a conflict");
        }

        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ScriptConflictException("Script not found: " + scriptId));
        Script conflicting = scriptRepository.findById(conflictsWithId)
                .orElseThrow(() -> new ScriptConflictException("Script not found: " + conflictsWithId));
        User admin = userRepository.getReferenceById(adminId);

        buildConflictEdge(script, conflicting, admin);

        buildConflictEdge(conflicting, script, admin);
    }

    private void buildConflictEdge(Script from, Script to, User admin) {
        if (!conflictRepository.existsByScriptIdAndConflictsWithScriptId(from.getId(), to.getId())) {
            ScriptConflict edge = new ScriptConflict();
            edge.setScriptId(from.getId());
            edge.setConflictsWithScriptId(to.getId());
            edge.setScript(from);
            edge.setConflictsWith(to);
            edge.setCreatedBy(admin);
            conflictRepository.save(edge);
        }
    }

    /**
     * Removes any conflict edges between the two scripts in both directions.
     */
    @Transactional
    public void removeConflict(UUID scriptId, UUID conflictsWithId) {
        conflictRepository.deleteById(
                new ScriptConflictId(scriptId, conflictsWithId));
        conflictRepository.deleteById(
                new ScriptConflictId(conflictsWithId, scriptId));
    }

    /**
     * Adds a dependency edge from {@code scriptId} to {@code requiresId},
     * guarding against circular dependencies.
     */
    @Transactional
    public void addDependency(UUID scriptId, UUID requiresId, UUID adminId) {
        if (scriptId.equals(requiresId)) {
            throw new CircularDependencyException("A script cannot depend on itself");
        }

        // A script cannot simultaneously require and conflict with the same script.
        if (conflictRepository.existsByScriptIdAndConflictsWithScriptId(scriptId, requiresId)
                || conflictRepository.existsByScriptIdAndConflictsWithScriptId(requiresId, scriptId)) {
            throw new ScriptConflictException("A script cannot be both a dependency and a conflict");
        }

        if (wouldCreateCircularDependency(scriptId, requiresId)) {
            throw new CircularDependencyException("This dependency would create a cycle");
        }

        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ScriptConflictException("Script not found: " + scriptId));
        Script required = scriptRepository.findById(requiresId)
                .orElseThrow(() -> new ScriptConflictException("Script not found: " + requiresId));
        User admin = userRepository.getReferenceById(adminId);

        if (!dependencyRepository.existsByScriptIdAndRequiresScriptId(scriptId, requiresId)) {
            ScriptDependency dep = new ScriptDependency();
            dep.setScriptId(script.getId());
            dep.setRequiresScriptId(required.getId());
            dep.setScript(script);
            dep.setRequires(required);
            dep.setCreatedBy(admin);
            dependencyRepository.save(dep);
        }
    }

    /**
     * Removes a single directed dependency edge between two scripts.
     */
    @Transactional
    public void removeDependency(UUID scriptId, UUID requiresId) {
        dependencyRepository.deleteById(
                new ScriptDependencyId(scriptId, requiresId));
    }

    /**
     * Returns a richer compatibility view including basic metadata about the
     * related scripts for use in admin UIs.
     */
    @Transactional(readOnly = true)
    public ScriptCompatibilityViewDto getCompatibilityView(UUID scriptId) {
        ScriptCompatibilityInfo info = getCompatibilityInfo(scriptId);

        List<UUID> relatedIds = new ArrayList<>();
        relatedIds.addAll(info.getConflictsWith());
        relatedIds.addAll(info.getRequires());

        Map<UUID, Script> relatedScripts = scriptRepository.findAllById(relatedIds).stream()
                .collect(HashMap::new, (m, s) -> m.put(s.getId(), s), Map::putAll);

        List<ScriptSummaryDto> conflicts = info.getConflictsWith().stream()
                .map(id -> ScriptSummaryDtoMapper.toScriptSummary(relatedScripts.get(id)))
                .toList();

        List<ScriptSummaryDto> dependencies = info.getRequires().stream()
                .map(id -> ScriptSummaryDtoMapper.toScriptSummary(relatedScripts.get(id)))
                .toList();

        return ScriptCompatibilityViewDto.builder()
                .scriptId(scriptId)
                .conflictsWith(conflicts)
                .requires(dependencies)
                .build();
    }

    private List<String> detectConflicts(List<UUID> scriptIds) {
        List<String> conflicts = new ArrayList<>();

        for (int i = 0; i < scriptIds.size(); i++) {
            for (int j = i + 1; j < scriptIds.size(); j++) {
                UUID a = scriptIds.get(i);
                UUID b = scriptIds.get(j);

                boolean ab = conflictRepository.existsByScriptIdAndConflictsWithScriptId(a, b);
                boolean ba = conflictRepository.existsByScriptIdAndConflictsWithScriptId(b, a);

                if (ab || ba) {
                    Script sa = scriptRepository.findById(a).orElse(null);
                    Script sb = scriptRepository.findById(b).orElse(null);
                    String left = sa != null ? sa.getName() : a.toString();
                    String right = sb != null ? sb.getName() : b.toString();
                    conflicts.add("'" + left + "' conflicts with '" + right + "'");
                }
            }
        }

        return conflicts;
    }

    /**
     * Adds all transitively required scripts to the {@code scriptIds} set and
     * returns the list of IDs that were not originally selected by the user.
     */
    private List<UUID> resolveDependencies(Set<UUID> scriptIds) {
        List<UUID> autoAdded = new ArrayList<>();
        Deque<UUID> toProcess = new ArrayDeque<>(scriptIds);
        Set<UUID> processed = new HashSet<>();

        while (!toProcess.isEmpty()) {
            UUID current = toProcess.removeFirst();
            if (!processed.add(current)) {
                continue;
            }

            for (ScriptDependency dep : dependencyRepository.findByScriptId(current)) {
                UUID requiredId = dep.getRequiresScriptId();
                if (!scriptIds.contains(requiredId)) {
                    scriptIds.add(requiredId);
                    autoAdded.add(requiredId);
                    toProcess.addLast(requiredId);
                }
            }
        }

        return autoAdded;
    }

    /**
     * Topologically sorts the given set of script IDs so that each dependency
     * appears before its dependants. Throws {@link CircularDependencyException}
     * if a cycle is detected.
     */
    private List<UUID> topologicalSort(Set<UUID> scriptIds) {
        Map<UUID, List<UUID>> adjacency = new HashMap<>();
        Map<UUID, Integer> inDegree = new HashMap<>();

        // Initialise maps.
        for (UUID id : scriptIds) {
            adjacency.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        // Build graph edges from dependency relations.
        for (UUID id : scriptIds) {
            for (ScriptDependency dep : dependencyRepository.findByScriptId(id)) {
                UUID required = dep.getRequiresScriptId();
                if (!scriptIds.contains(required)) {
                    continue;
                }
                adjacency.get(required).add(id);
                inDegree.put(id, inDegree.get(id) + 1);
            }
        }

        // Kahn's algorithm.
        Queue<UUID> queue = new ArrayDeque<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<UUID> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            sorted.add(current);
            for (UUID next : adjacency.getOrDefault(current, List.of())) {
                int updated = inDegree.get(next) - 1;
                inDegree.put(next, updated);
                if (updated == 0) {
                    queue.add(next);
                }
            }
        }

        if (sorted.size() != scriptIds.size()) {
            log.warn("Detected circular dependency in script graph: expected {} nodes, sorted {}",
                    scriptIds.size(), sorted.size());
            throw new CircularDependencyException("Circular dependency detected between scripts");
        }

        return sorted;
    }

    /**
     * Returns {@code true} if introducing a dependency edge {@code scriptId -> newDependencyId}
     * would create a cycle in the dependency graph.
     */
    private boolean wouldCreateCircularDependency(UUID scriptId, UUID newDependencyId) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();
        queue.add(newDependencyId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(scriptId)) {
                return true;
            }
            for (ScriptDependency dep : dependencyRepository.findByScriptId(current)) {
                queue.add(dep.getRequiresScriptId());
            }
        }

        return false;
    }
}

