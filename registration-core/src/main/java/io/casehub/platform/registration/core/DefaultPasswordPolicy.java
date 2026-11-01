package io.casehub.platform.registration.core;

import io.casehub.platform.registration.BreachDetector;
import io.casehub.platform.registration.PasswordPolicy;
import io.casehub.platform.registration.PasswordPolicyResult;

import java.util.ArrayList;
import java.util.List;

public class DefaultPasswordPolicy implements PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private final BreachDetector breachDetector;

    public DefaultPasswordPolicy(BreachDetector breachDetector) {
        this.breachDetector = breachDetector;
    }

    @Override
    public PasswordPolicyResult validate(String password) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            violations.add("Password must not be empty");
            return PasswordPolicyResult.invalid(violations);
        }

        if (password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters");
        }

        if (password.length() > MAX_LENGTH) {
            violations.add("Password must not exceed " + MAX_LENGTH + " characters");
        }

        if (countCharacterClasses(password) < 2) {
            violations.add("Password must contain at least 2 character classes (lowercase, uppercase, digit, special)");
        }

        if (violations.isEmpty() && breachDetector != null) {
            try {
                if (breachDetector.isBreached(password)) {
                    violations.add("Password found in known data breach");
                }
            } catch (Exception e) {
                warnings.add("Breach detection unavailable");
            }
        }

        if (!violations.isEmpty()) {
            return PasswordPolicyResult.invalid(violations, warnings);
        }
        if (!warnings.isEmpty()) {
            return PasswordPolicyResult.withWarnings(warnings);
        }
        return PasswordPolicyResult.ok();
    }

    private static int countCharacterClasses(String password) {
        int classes = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        return classes;
    }
}
