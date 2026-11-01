package io.casehub.platform.registration.core;

import io.casehub.platform.registration.PasswordPolicyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPasswordPolicyTest {

    private final DefaultPasswordPolicy policyNoBreachCheck = new DefaultPasswordPolicy(null);

    @Test
    void validPassword_passes() {
        var result = policyNoBreachCheck.validate("Str0ngPass!");
        assertTrue(result.valid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void tooShort_rejected() {
        var result = policyNoBreachCheck.validate("Ab1!");
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("at least 8")));
    }

    @Test
    void tooLong_rejected() {
        var result = policyNoBreachCheck.validate("A1" + "x".repeat(127));
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("128")));
    }

    @Test
    void singleCharacterClass_rejected() {
        var result = policyNoBreachCheck.validate("alllowercase");
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("character")));
    }

    @Test
    void twoCharacterClasses_passes() {
        var result = policyNoBreachCheck.validate("lowercase1");
        assertTrue(result.valid());
    }

    @Test
    void exactlyMinLength_passes() {
        var result = policyNoBreachCheck.validate("Abcdefg1");
        assertTrue(result.valid());
    }

    @Test
    void exactlyMaxLength_passes() {
        String pw = "A1" + "x".repeat(126);
        assertEquals(128, pw.length());
        var result = policyNoBreachCheck.validate(pw);
        assertTrue(result.valid());
    }

    @Test
    void nullPassword_rejected() {
        var result = policyNoBreachCheck.validate(null);
        assertFalse(result.valid());
    }

    @Test
    void emptyPassword_rejected() {
        var result = policyNoBreachCheck.validate("");
        assertFalse(result.valid());
    }

    @Test
    void multipleViolations_allCollected() {
        var result = policyNoBreachCheck.validate("aaa");
        assertFalse(result.valid());
        assertTrue(result.violations().size() >= 2);
    }

    @Test
    void breachedPassword_rejected() {
        var policy = new DefaultPasswordPolicy(password -> true);
        var result = policy.validate("Str0ngPass!");
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("breach")));
    }

    @Test
    void cleanPassword_withBreachCheck_passes() {
        var policy = new DefaultPasswordPolicy(password -> false);
        var result = policy.validate("Str0ngPass!");
        assertTrue(result.valid());
    }

    @Test
    void breachCheckThrows_warningNotViolation() {
        var policy = new DefaultPasswordPolicy(password -> {
            throw new RuntimeException("Network error");
        });
        var result = policy.validate("Str0ngPass!");
        assertTrue(result.valid());
        assertFalse(result.warnings().isEmpty());
    }
}
