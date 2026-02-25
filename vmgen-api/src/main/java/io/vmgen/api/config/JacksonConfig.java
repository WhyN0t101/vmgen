package io.vmgen.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/*
  Configures the primary {@link ObjectMapper} for the API, ensuring
  proper support for {@code java.time} types and ISO-8601 date formats.
 */
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Support java.time.* types like Instant
        mapper.registerModule(new JavaTimeModule());
        // Keep timestamps as ISO-8601 strings instead of numeric timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}


