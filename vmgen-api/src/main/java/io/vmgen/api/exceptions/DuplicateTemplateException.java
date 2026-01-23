package io.vmgen.api.exceptions;

public class DuplicateTemplateException extends VmgenApiException {
    public DuplicateTemplateException(String message) {
        super(message);
    }
    
    public DuplicateTemplateException(String identifier, String version) {
        super("Template with identifier '" + identifier + "' and version '" + version + "' already exists");
    }
}
