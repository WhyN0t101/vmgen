package io.vmgen.api.models.template;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TemplateStatus {
    DRAFT,
    VALIDATING,
    ACTIVE,
    FAILED,
    DEPRECATED;

    @JsonValue
    public String getValue() {
        return name().toLowerCase();
    }
}