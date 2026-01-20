package io.vmgen.api.exceptions;

public class SessionExpiredException extends VmgenApiException {
    public SessionExpiredException() {
        super("Session expired, please login again");
    }
}


