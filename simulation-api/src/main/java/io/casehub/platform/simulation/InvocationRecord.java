package io.casehub.platform.simulation;

import java.time.Instant;

public record InvocationRecord<I, O>(
        String tenancyId,
        String key,
        I input,
        O output,
        Instant recordedAt
) {}
