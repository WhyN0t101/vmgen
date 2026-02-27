package io.vmgen.api.models.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA attribute converter that persists {@link ValidationRules} as JSONB.
 */
@Converter
@Slf4j
public class ValidationRulesConverter implements AttributeConverter<ValidationRules, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ValidationRules attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise ValidationRules to JSON", e);
            return null;
        }
    }

    @Override
    public ValidationRules convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, ValidationRules.class);
        } catch (Exception e) {
            log.warn("Failed to deserialise ValidationRules from JSON", e);
            return null;
        }
    }
}

