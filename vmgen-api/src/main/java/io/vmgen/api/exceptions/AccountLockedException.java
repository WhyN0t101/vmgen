package io.vmgen.api.exceptions;

public class AccountLockedException extends VmgenApiException {
    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Account is temporarily locked");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
