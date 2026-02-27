package io.vmgen.api.models.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA attribute converter that persists {@link RandomConfig} as JSONB.
 */
@Converter
@Slf4j
public class RandomConfigConverter implements AttributeConverter<RandomConfig, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(RandomConfig attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise RandomConfig to JSON", e);
            return null;
        }
    }

    @Override
    public RandomConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, RandomConfig.class);
        } catch (Exception e) {
            log.warn("Failed to deserialise RandomConfig from JSON", e);
            return null;
        }
    }
}

