package io.vmgen.api.models.script;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA attribute converter that persists {@link ValidationRules} as JSONB.
 *
 * <p>Uses NON_NULL serialisation so that unset rule fields are never written
 * to the database column, keeping JSONB payloads minimal and preventing null
 * round-trips back to the frontend.</p>
 */
@Converter
@Slf4j
public class ValidationRulesConverter implements AttributeConverter<ValidationRules, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

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

