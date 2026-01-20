package io.vmgen.api.exceptions;

public abstract class VmgenApiException extends RuntimeException {
    private final String publicMessage;

    protected VmgenApiException(String publicMessage) {
        super(publicMessage);
        this.publicMessage = publicMessage;
    }

    protected VmgenApiException(String publicMessage, Throwable cause) {
        super(publicMessage, cause);
        this.publicMessage = publicMessage;
    }

    public String getPublicMessage() {
        return publicMessage;
    }
}


