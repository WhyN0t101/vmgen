package io.vmgen.api.exceptions;

public class TemplateNotFoundException extends VmgenApiException {
    public TemplateNotFoundException(String message) {
        super(message);
    }
    
    public TemplateNotFoundException() {
        super("Template not found");
    }
}
