package io.vmgen.api.service;

import io.vmgen.api.dto.script.CreateScriptParameterRequest;
import io.vmgen.api.dto.script.ParameterChoiceDto;
import io.vmgen.api.dto.script.ScriptParameterChoiceDefinition;
import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.dto.script.UpdateScriptParameterRequest;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.exceptions.ParameterValidationException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.models.script.ParameterType;
import io.vmgen.api.models.script.RandomConfig;
import io.vmgen.api.models.script.ScriptParameter;
import io.vmgen.api.models.script.ScriptParameterChoice;
import io.vmgen.api.models.script.ValidationRules;
import io.vmgen.api.repository.ScriptParameterChoiceRepository;
import io.vmgen.api.repository.ScriptParameterRepository;
import io.vmgen.api.repository.ScriptRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptParameterService {

    private final ScriptRepository scriptRepository;
    private final ScriptParameterRepository parameterRepository;
    private final ScriptParameterChoiceRepository choiceRepository;

    private static final SecureRandom RANDOM = new SecureRandom();


    @Transactional
    public List<ScriptParameterDto> listParameters(UUID scriptId) {
        scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + scriptId));

        return parameterRepository.findByScript_IdOrderByDisplayOrderAsc(scriptId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ScriptParameterDto createParameter(UUID scriptId, CreateScriptParameterRequest request) {
        var script = scriptRepository.findById(scriptId)
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
            saved.setChoices(persistChoices(saved, request.getChoices()));
        }

        return toDto(saved);
    }

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

        // Replace choices if provided.
        if (request.getChoices() != null) {
            choiceRepository.deleteByParameter_Id(existing.getId());
            existing.setChoices(persistChoices(existing, request.getChoices()));
        }

        ScriptParameter saved = parameterRepository.save(existing);
        return toDto(saved);
    }

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
            io.vmgen.api.models.script.ValueSource valueSource,
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

    private List<ScriptParameterChoice> persistChoices(
            ScriptParameter parameter,
            List<ScriptParameterChoiceDefinition> definitions
    ) {
        List<ScriptParameterChoice> choices = new ArrayList<>();
        for (ScriptParameterChoiceDefinition def : definitions) {
            ScriptParameterChoice choice = new ScriptParameterChoice();
            choice.setParameter(parameter);
            choice.setChoiceValue(def.getValue());
            choice.setChoiceLabel(def.getLabel());
            choice.setDescription(def.getDescription());
            choice.setDisplayOrder(def.getDisplayOrder() != null ? def.getDisplayOrder() : 0);
            choices.add(choiceRepository.save(choice));
        }
        return choices;
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
                && param.getValueSource() == io.vmgen.api.models.script.ValueSource.USER_INPUT) {
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
        ValidationRules rules = param.getValidationRules();
        if (rules == null) {
            return;
        }

        List<String> errors = new ArrayList<>();

        switch (param.getParameterType()) {
            case STRING, PATH -> validateStringLike(param, value, rules, errors);
            case INTEGER -> validateIntegerLike(param, value, rules, errors);
            case BOOLEAN, CHOICE -> {
                // Validated elsewhere (e.g. by choice set); nothing to do here.
            }
        }

        if (!errors.isEmpty()) {
            throw new ParameterValidationException("Parameter validation failed", errors);
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

    private static @NonNull String getAlphabet(RandomConfig config) {
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

