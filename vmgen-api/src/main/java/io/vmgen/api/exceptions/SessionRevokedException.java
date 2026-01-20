package io.vmgen.api.exceptions;

public class SessionRevokedException extends VmgenApiException {
    public SessionRevokedException() {
        super("Session no longer valid");
    }
}


