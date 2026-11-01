package io.casehub.platform.registration;

import java.util.List;

public record PasswordPolicyResult(
    boolean valid,
    List<String> violations,
    List<String> warnings
) {
    public static PasswordPolicyResult ok() {
        return new PasswordPolicyResult(true, List.of(), List.of());
    }

    public static PasswordPolicyResult withWarnings(List<String> warnings) {
        return new PasswordPolicyResult(true, List.of(), List.copyOf(warnings));
    }

    public static PasswordPolicyResult invalid(List<String> violations) {
        return new PasswordPolicyResult(false, List.copyOf(violations), List.of());
    }

    public static PasswordPolicyResult invalid(List<String> violations, List<String> warnings) {
        return new PasswordPolicyResult(false, List.copyOf(violations), List.copyOf(warnings));
    }
}
