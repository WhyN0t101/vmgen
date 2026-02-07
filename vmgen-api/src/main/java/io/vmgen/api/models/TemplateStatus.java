package io.vmgen.api.models;

public enum TemplateStatus {
    DRAFT,
    VALIDATING,
    ACTIVE,
    FAILED,
    DEPRECATED;

    public String getValue() {
        return name().toLowerCase();
    }
}