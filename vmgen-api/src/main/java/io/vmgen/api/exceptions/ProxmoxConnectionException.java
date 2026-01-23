package io.vmgen.api.exceptions;

public class ProxmoxConnectionException extends VmgenApiException {
    public ProxmoxConnectionException(String message) {
        super(message);
    }
    
    public ProxmoxConnectionException(String message, Throwable cause) {
        super(message);
        this.initCause(cause);
    }
}
