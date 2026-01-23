package io.vmgen.api.exceptions;

public class RateLimitExceededException extends VmgenApiException {
    private final long resetSeconds;

    public RateLimitExceededException(long resetSeconds) {
        super("Too many requests. Please try again later.");
        this.resetSeconds = resetSeconds;
    }

    public long getResetSeconds() {
        return resetSeconds;
    }
}
