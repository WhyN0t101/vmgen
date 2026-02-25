package io.vmgen.api.utils;

import io.vmgen.api.exceptions.ValidationException;
import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 12;

    private PasswordPolicyValidator() {}

    public static void validate(final String password) {
        final List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Password is required");
            throw new ValidationException("Validation failed", errors);
        }

        // Single-pass collection of character properties
        boolean hasUpper   = false;
        boolean hasLower   = false;
        boolean hasDigit   = false;
        boolean hasSymbol  = false;

        for (int i = 0; i < password.length(); i++) {
            final char c = password.charAt(i);
            if (Character.isUpperCase(c))                          hasUpper  = true;
            else if (Character.isLowerCase(c))                     hasLower  = true;
            else if (Character.isDigit(c))                         hasDigit  = true;
            else if (!Character.isLetterOrDigit(c))                hasSymbol = true;
        }

        if (password.length() < MIN_LENGTH)
            errors.add("Password must be at least " + MIN_LENGTH + " characters");
        if (!hasUpper)   errors.add("Password must contain an uppercase letter");
        if (!hasLower)   errors.add("Password must contain a lowercase letter");
        if (!hasDigit)   errors.add("Password must contain a digit");
        if (!hasSymbol)  errors.add("Password must contain a symbol");

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}


