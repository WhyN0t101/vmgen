package io.vmgen.api.exceptions;

public class AuthenticationFailedException extends VmgenApiException {
    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}


