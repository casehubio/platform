package io.casehub.platform.agent.session;

import io.casehub.platform.agent.config.PoolDeclaration;

import java.time.Duration;

public record SessionPoolConfig(
        String name,
        String backendKey,
        int minActive,
        int maxActive,
        Duration idleTimeout,
        Duration checkoutTimeout,
        int salience,
        ClearingPolicy clearingPolicy,
        int clearingThreshold,
        int clearingTurns,
        int maxPromptTokens
) {
    public SessionPoolConfig(String name, String backendKey,
            int minActive, int maxActive,
            Duration idleTimeout, Duration checkoutTimeout) {
        this(name, backendKey, minActive, maxActive, idleTimeout, checkoutTimeout,
                0, ClearingPolicy.MANUAL, 0, 0, 0);
    }

    public SessionPoolConfig {
        if (minActive < 0) throw new IllegalArgumentException("minActive must be >= 0");
        if (maxActive < 1) throw new IllegalArgumentException("maxActive must be >= 1");
        if (maxActive < minActive) throw new IllegalArgumentException("maxActive must be >= minActive");
        if (idleTimeout == null) idleTimeout = Duration.ofSeconds(300);
        if (checkoutTimeout == null) checkoutTimeout = Duration.ofSeconds(30);
        if (clearingPolicy == null) clearingPolicy = ClearingPolicy.MANUAL;
    }

    public static SessionPoolConfig fromDeclaration(PoolDeclaration decl) {
        var ext = decl.extensions();
        long idleSecs = ext.containsKey("idle-timeout-seconds")
                ? ((Number) ext.get("idle-timeout-seconds")).longValue() : 300;
        long checkoutSecs = ext.containsKey("checkout-timeout-seconds")
                ? ((Number) ext.get("checkout-timeout-seconds")).longValue() : 30;

        var clearing = decl.clearing();
        ClearingPolicy clearingPolicy = ClearingPolicy.MANUAL;
        int clearingThreshold = 0;
        int clearingTurns = 0;
        if (clearing != null) {
            var policy = (String) clearing.get("policy");
            if (policy != null) {
                clearingPolicy = switch (policy) {
                    case "every-call" -> ClearingPolicy.EVERY_CALL;
                    case "turn-count" -> ClearingPolicy.AFTER_N_TURNS;
                    case "size-aware" -> ClearingPolicy.SIZE_AWARE;
                    default -> ClearingPolicy.MANUAL;
                };
            }
            if (clearing.containsKey("threshold")) {
                clearingThreshold = ((Number) clearing.get("threshold")).intValue();
            }
            if (clearing.containsKey("turns")) {
                clearingTurns = ((Number) clearing.get("turns")).intValue();
            }
        }

        return new SessionPoolConfig(
                decl.name(), decl.backend(),
                decl.minActive(), decl.maxActive(),
                Duration.ofSeconds(idleSecs), Duration.ofSeconds(checkoutSecs),
                decl.salience(), clearingPolicy, clearingThreshold, clearingTurns,
                decl.maxPromptTokens());
    }
}
