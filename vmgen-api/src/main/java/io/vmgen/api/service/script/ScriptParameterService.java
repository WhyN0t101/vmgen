package io.vmgen.api.service.script;

import io.vmgen.api.dto.script.CreateScriptParameterRequest;
import io.vmgen.api.dto.script.ParameterChoiceDto;
import io.vmgen.api.dto.script.ScriptParameterChoiceDefinition;
import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.dto.script.UpdateScriptParameterRequest;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.exceptions.ParameterValidationException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.models.script.*;
import io.vmgen.api.repository.script.ScriptParameterChoiceRepository;
import io.vmgen.api.repository.script.ScriptParameterRepository;
import io.vmgen.api.repository.script.ScriptRepository;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static io.vmgen.api.models.script.ValueSource.USER_INPUT;


@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptParameterService {

    private final ScriptRepository scriptRepository;
    private final ScriptParameterRepository parameterRepository;
    private final ScriptParameterChoiceRepository choiceRepository;

    private static final SecureRandom RANDOM = new SecureRandom();


    /**
     * Lists all parameters for the given script, ordered by display order.
     * Throws {@link ResourceNotFoundException} if the script does not exist.
     */
    @Transactional(readOnly = true)
    public List<ScriptParameterDto> listParameters(UUID scriptId) {

        scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + scriptId));

        return parameterRepository.findByScript_IdOrderByDisplayOrderAsc(scriptId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Lists only user-facing parameters for the given script.
     * Filters to {@link ValueSource#USER_INPUT} and {@link ValueSource#USER_INPUT_OR_RANDOM} sources.
     */
    @Transactional(readOnly = true)
    public List<ScriptParameterDto> listUserInputParameters(UUID scriptId) {
        return listParameters(scriptId).stream()
                .filter(p -> p.getValueSource() == USER_INPUT
                        || p.getValueSource() == ValueSource.USER_INPUT_OR_RANDOM)
                .toList();
    }

    /**
     * Creates a new parameter for the given script.
     * Throws {@link ConflictException} if a parameter with the same name already exists.
     */
    @Transactional
    public ScriptParameterDto createParameter(UUID scriptId, CreateScriptParameterRequest request) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + scriptId));

        if (parameterRepository.existsByScript_IdAndParameterName(scriptId, request.getParameterName())) {
            throw new ConflictException("Parameter with name " + request.getParameterName() + " already exists");
        }

        ScriptParameter parameter = new ScriptParameter();
        parameter.setScript(script);
        parameter.setParameterName(request.getParameterName());
        applyMutableFields(parameter, request.getDisplayName(), request.getDescription(),
                request.getParameterType(), request.getValueSource(), request.getDefaultValue(),
                request.getIsRequired(), request.getValidationRules(), request.getRandomConfig(),
                request.getUserCanOverride(), request.getDisplayOrder());

        ScriptParameter saved = parameterRepository.save(parameter);

        if (request.getChoices() != null && !request.getChoices().isEmpty()) {
            saved.getChoices().addAll(persistChoices(saved, request.getChoices()));
        }

        return toDto(saved);
    }

    /**
     * Updates an existing parameter. Replaces choices entirely if provided in the request.
     * Throws {@link ConflictException} if the parameter does not belong to the given script.
     */
    @Transactional
    public ScriptParameterDto updateParameter(UUID scriptId, UUID parameterId, UpdateScriptParameterRequest request) {
        ScriptParameter existing = parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter not found: " + parameterId));

        if (!existing.getScript().getId().equals(scriptId)) {
            throw new ConflictException("Parameter does not belong to this script");
        }

        applyMutableFields(existing, request.getDisplayName(), request.getDescription(),
                request.getParameterType(), request.getValueSource(), request.getDefaultValue(),
                request.getIsRequired(), request.getValidationRules(), request.getRandomConfig(),
                request.getUserCanOverride(), request.getDisplayOrder());

        // Replace choices if provided — mutate the PersistentBag in-place.
        // orphanRemoval = true on the mapping handles deletion of cleared entries.
        if (request.getChoices() != null) {
            existing.getChoices().clear();
            existing.getChoices().addAll(persistChoices(existing, request.getChoices()));
        }

        ScriptParameter saved = parameterRepository.save(existing);
        return toDto(saved);
    }

    /**
     * Deletes a parameter and its associated choices via orphan removal.
     * Throws {@link ConflictException} if the parameter does not belong to the given script.
     */
    @Transactional
    public void deleteParameter(UUID scriptId, UUID parameterId) {
        ScriptParameter existing = parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter not found: " + parameterId));

        if (!existing.getScript().getId().equals(scriptId)) {
            throw new ConflictException("Parameter does not belong to this script");
        }

        parameterRepository.delete(existing);
    }

    private void applyMutableFields(
            ScriptParameter parameter,
            String displayName,
            String description,
            ParameterType parameterType,
            ValueSource valueSource,
            String defaultValue,
            Boolean isRequired,
            ValidationRules validationRules,
            RandomConfig randomConfig,
            Boolean userCanOverride,
            Integer displayOrder
    ) {
        parameter.setDisplayName(displayName);
        parameter.setDescription(description);
        parameter.setParameterType(parameterType);
        parameter.setValueSource(valueSource);
        parameter.setDefaultValue(defaultValue);
        parameter.setIsRequired(isRequired != null ? isRequired : Boolean.TRUE);
        parameter.setValidationRules(validationRules);
        parameter.setRandomConfig(randomConfig);
        parameter.setUserCanOverride(userCanOverride != null ? userCanOverride : Boolean.FALSE);
        parameter.setDisplayOrder(displayOrder != null ? displayOrder : 0);
    }

    private List<ScriptParameterChoice> persistChoices(ScriptParameter parameter, List<ScriptParameterChoiceDefinition> definitions) {
        List<ScriptParameterChoice> choices = definitions.stream().map(def -> {
            ScriptParameterChoice choice = new ScriptParameterChoice();
            choice.setParameter(parameter);
            choice.setChoiceValue(def.getValue());
            choice.setChoiceLabel(def.getLabel());
            choice.setDescription(def.getDescription());
            choice.setDisplayOrder(def.getDisplayOrder() != null ? def.getDisplayOrder() : 0);
            return choice;
        }).toList();
        return choiceRepository.saveAll(choices);
    }

    private ScriptParameterDto toDto(ScriptParameter parameter) {
        List<ParameterChoiceDto> choiceDtos = parameter.getChoices() == null ? List.of()
                : parameter.getChoices().stream()
                .map(c -> ParameterChoiceDto.builder()
                        .id(c.getId())
                        .value(c.getChoiceValue())
                        .label(c.getChoiceLabel())
                        .description(c.getDescription())
                        .displayOrder(c.getDisplayOrder())
                        .build())
                .toList();

        return ScriptParameterDto.builder()
                .id(parameter.getId())
                .parameterName(parameter.getParameterName())
                .displayName(parameter.getDisplayName())
                .description(parameter.getDescription())
                .parameterType(parameter.getParameterType())
                .valueSource(parameter.getValueSource())
                .defaultValue(parameter.getDefaultValue())
                .isRequired(parameter.getIsRequired())
                .validationRules(parameter.getValidationRules())
                .randomConfig(parameter.getRandomConfig())
                .userCanOverride(parameter.getUserCanOverride())
                .displayOrder(parameter.getDisplayOrder())
                .choices(choiceDtos)
                .build();
    }


    /**
     * Processes user-provided parameter values for a given script and returns a
     * map of final values alongside a flag indicating which values were generated
     * automatically.
     */
    @Transactional
    public ParameterProcessingResult processParameters(
            UUID scriptId,
            Map<String, String> userValues,
            Map<String, Boolean> randomiseFlags
    ) {
        List<ScriptParameter> definitions = parameterRepository.findByScript_IdOrderByDisplayOrderAsc(scriptId);
        if (definitions.isEmpty()) {
            return ParameterProcessingResult.empty();
        }

        Map<String, String> finalValues = new HashMap<>();
        Map<String, Boolean> autoGenerated = new HashMap<>();
        List<String> validationErrors = new ArrayList<>();

        for (ScriptParameter param : definitions) {
            try {
                String value = resolveValue(param, userValues, randomiseFlags);
                finalValues.put(param.getParameterName(), value);
                autoGenerated.put(param.getParameterName(), wasAutoGenerated(param, randomiseFlags));
            } catch (ParameterValidationException ex) {
                validationErrors.addAll(ex.getErrors());
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new ParameterValidationException("One or more parameter values are invalid", validationErrors);
        }

        return new ParameterProcessingResult(finalValues, autoGenerated);
    }

    private boolean wasAutoGenerated(ScriptParameter param, Map<String, Boolean> randomiseFlags) {
        return switch (param.getValueSource()) {
            case AUTO_RANDOM, AUTO_CHOICE -> true;
            case USER_INPUT_OR_RANDOM -> Boolean.TRUE.equals(randomiseFlags.get(param.getParameterName()));
            case USER_INPUT -> false;
        };
    }

    private String resolveValue(
            ScriptParameter param,
            Map<String, String> userValues,
            Map<String, Boolean> randomiseFlags
    ) {
        userValues = userValues != null ? userValues : Map.of();
        randomiseFlags = randomiseFlags != null ? randomiseFlags : Map.of();

        return switch (param.getValueSource()) {
            case USER_INPUT -> processUserInput(param, userValues);
            case AUTO_RANDOM -> generateRandomValue(param);
            case AUTO_CHOICE -> pickRandomChoice(param);
            case USER_INPUT_OR_RANDOM -> {
                boolean shouldRandomise = Boolean.TRUE.equals(randomiseFlags.get(param.getParameterName()));
                if (shouldRandomise) {
                    yield generateRandomValue(param);
                }
                yield processUserInput(param, userValues);
            }
        };
    }

    private String processUserInput(ScriptParameter param, Map<String, String> userValues) {
        String raw = userValues.get(param.getParameterName());

        if ((raw == null || raw.isBlank()) && Boolean.TRUE.equals(param.getIsRequired())
                && param.getValueSource() == USER_INPUT) {
            throw new ParameterValidationException(
                    param.getDisplayName() + " is required");
        }

        String value = raw;
        if ((value == null || value.isBlank()) && param.getDefaultValue() != null) {
            value = param.getDefaultValue();
        }

        if (value != null) {
            validateValue(param, value);
        }

        return value;
    }

    private void validateValue(ScriptParameter param, String value) {
        List<String> errors = new ArrayList<>();
        ValidationRules rules = param.getValidationRules();

        switch (param.getParameterType()) {
            case STRING -> {
                if (rules != null) validateStringLike(param, value, rules, errors);
            }
            case PATH -> {
                validatePathFormat(param, value, errors);
                if (rules != null) validateStringLike(param, value, rules, errors);
            }
            case INTEGER -> {
                if (rules != null) validateIntegerLike(param, value, rules, errors);
                else validateIntegerFormat(param, value, errors);
            }
            case BOOLEAN -> validateBoolean(param, value, errors);
            case CHOICE -> validateChoice(param, value, errors);
        }

        if (!errors.isEmpty()) {
            throw new ParameterValidationException("Parameter validation failed", errors);
        }
    }

    /** Ensures a PATH value is absolute, free of traversal sequences, and has no whitespace. */
    private void validatePathFormat(ScriptParameter param, String value, List<String> errors) {
        if (!value.startsWith("/")) {
            errors.add(param.getDisplayName() + " must be an absolute path (must start with /)");
        }
        if (value.contains("..")) {
            errors.add(param.getDisplayName() + " must not contain path traversal sequences (..)");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            errors.add(param.getDisplayName() + " must not contain whitespace");
        }
    }

    /** Ensures the value is a valid integer even when no range rules are defined. */
    private void validateIntegerFormat(ScriptParameter param, String value, List<String> errors) {
        try {
            Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            errors.add(param.getDisplayName() + " must be a whole number");
        }
    }

    /** Ensures a BOOLEAN value is exactly "true" or "false" (case-insensitive). */
    private void validateBoolean(ScriptParameter param, String value, List<String> errors) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            errors.add(param.getDisplayName() + " must be 'true' or 'false'");
        }
    }

    /** Ensures a CHOICE value is one of the admin-defined allowed choice values. */
    private void validateChoice(ScriptParameter param, String value, List<String> errors) {
        List<ScriptParameterChoice> choices = param.getChoices();
        if (choices == null || choices.isEmpty()) {
            // No choices defined — admin configuration error; reject to be safe.
            errors.add(param.getDisplayName() + " has no defined choices");
            return;
        }
        boolean valid = choices.stream().anyMatch(c -> c.getChoiceValue().equals(value));
        if (!valid) {
            errors.add(param.getDisplayName() + " must be one of the allowed values");
        }
    }

    private void validateStringLike(
            ScriptParameter param,
            String value,
            ValidationRules rules,
            List<String> errors
    ) {
        if (rules.getPattern() != null && !value.matches(rules.getPattern())) {
            errors.add(param.getDisplayName() + " format is invalid");
        }
        if (rules.getMinLength() != null && value.length() < rules.getMinLength()) {
            errors.add(param.getDisplayName() + " must be at least " + rules.getMinLength() + " characters");
        }
        if (rules.getMaxLength() != null && value.length() > rules.getMaxLength()) {
            errors.add(param.getDisplayName() + " must be at most " + rules.getMaxLength() + " characters");
        }
    }

    private void validateIntegerLike(
            ScriptParameter param,
            String value,
            ValidationRules rules,
            List<String> errors
    ) {
        try {
            int parsed = Integer.parseInt(value);
            if (rules.getMin() != null && parsed < rules.getMin()) {
                errors.add(param.getDisplayName() + " must be at least " + rules.getMin());
            }
            if (rules.getMax() != null && parsed > rules.getMax()) {
                errors.add(param.getDisplayName() + " must be at most " + rules.getMax());
            }
        } catch (NumberFormatException ex) {
            errors.add(param.getDisplayName() + " must be a valid number");
        }
    }

    private String generateRandomValue(ScriptParameter param) {
        RandomConfig config = param.getRandomConfig();
        if (config == null) {
            throw new IllegalStateException("Random configuration missing for parameter " + param.getParameterName());
        }

        String generated;
        if (param.getParameterType() == ParameterType.INTEGER) {
            generated = String.valueOf(generateRandomInteger(config));
        } else {
            generated = generateRandomString(config);
        }

        // Safety net: ensure generated value still passes validation.
        validateValue(param, generated);
        return generated;
    }

    private String generateRandomString(RandomConfig config) {
        int length = config.getLength() != null ? config.getLength() : 16;
        if (length <= 0) {
            length = 16;
        }

        String alphabet = getAlphabet(config);

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(alphabet.length());
            sb.append(alphabet.charAt(index));
        }
        return sb.toString();
    }

    private static String getAlphabet(RandomConfig config) {
        String charset = config.getCharset() != null ? config.getCharset() : "alphanumeric";
        return switch (charset) {
            case "alphanumeric_special" ->
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*-_=+";
            case "hex" -> "0123456789abcdef";
            case "numeric" -> "0123456789";
            default -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        };
    }

    private int generateRandomInteger(RandomConfig config) {
        int min = config.getMin() != null ? config.getMin() : 0;
        int max = config.getMax() != null ? config.getMax() : 100;
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return RANDOM.nextInt((max - min) + 1) + min;
    }

    private String pickRandomChoice(ScriptParameter param) {
        List<ScriptParameterChoice> choices = param.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices defined for parameter " + param.getParameterName());
        }
        int index = RANDOM.nextInt(choices.size());
        return choices.get(index).getChoiceValue();
    }


    /**
     * Result of parameter processing for a single script.
     */
    public record ParameterProcessingResult(
            Map<String, String> parameterValues,
            Map<String, Boolean> autoGeneratedFlags
    ) {
        public static ParameterProcessingResult empty() {
            return new ParameterProcessingResult(Map.of(), Map.of());
        }
    }
}

