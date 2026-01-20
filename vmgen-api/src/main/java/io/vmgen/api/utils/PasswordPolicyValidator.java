package io.vmgen.api.utils;

import io.vmgen.api.exceptions.ValidationException;
import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicyValidator {
    private PasswordPolicyValidator() {}

    public static void validate(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null) {
            errors.add("Password is required");
            throw new ValidationException("Validation failed", errors);
        }
        if (password.length() < 12) errors.add("Password must be at least 12 characters");
        if (password.chars().noneMatch(Character::isUpperCase)) errors.add("Password must contain an uppercase letter");
        if (password.chars().noneMatch(Character::isLowerCase)) errors.add("Password must contain a lowercase letter");
        if (password.chars().noneMatch(Character::isDigit)) errors.add("Password must contain a digit");
        if (password.chars().allMatch(Character::isLetterOrDigit)) errors.add("Password must contain a symbol");

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}


