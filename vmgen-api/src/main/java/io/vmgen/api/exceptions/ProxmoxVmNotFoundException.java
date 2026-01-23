package io.vmgen.api.exceptions;

public class ProxmoxVmNotFoundException extends VmgenApiException {
    public ProxmoxVmNotFoundException(String message) {
        super(message);
    }
}
