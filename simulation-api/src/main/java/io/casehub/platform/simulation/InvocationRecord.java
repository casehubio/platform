package io.casehub.platform.simulation;

import java.time.Instant;

public record InvocationRecord<I, O>(
        String tenancyId,
        String key,
        I input,
        O output,
        Instant recordedAt
) {
    public static <I, O> InvocationRecord<I, O> of(String tenancyId, I input, O output) {
        return new InvocationRecord<>(tenancyId, null, input, output, Instant.now());
    }

    public static <I, O> InvocationRecord<I, O> of(String tenancyId, String key, I input, O output) {
        return new InvocationRecord<>(tenancyId, key, input, output, Instant.now());
    }
}
