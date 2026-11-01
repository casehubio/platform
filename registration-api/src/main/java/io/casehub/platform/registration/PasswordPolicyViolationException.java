package io.casehub.platform.registration;

import java.util.List;

public class PasswordPolicyViolationException extends RuntimeException {

    private final List<String> violations;

    public PasswordPolicyViolationException(List<String> violations) {
        super("Password policy violated: " + String.join(", ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
