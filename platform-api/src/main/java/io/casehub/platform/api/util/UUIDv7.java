package io.casehub.platform.api.util;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID v7 generator per RFC 9562 §5.7. Produces time-ordered UUIDs with millisecond
 * precision timestamp prefix and monotonic sequence for same-millisecond UUIDs.
 *
 * <p>UUID v7 layout (128 bits):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        unix_ts_ms (48 bits)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ver (4) |       sequence (12 bits)    |var(2)|  rand_b (62)  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <p>Time-ordered property: UUIDs generated at different milliseconds sort correctly
 * lexicographically and numerically. Same-millisecond UUIDs use a monotonically
 * increasing 12-bit sequence counter, ensuring deterministic ordering for cursor
 * pagination stability.
 *
 * <p>Thread-local state: sequence counter and last timestamp are thread-local, so
 * UUIDs are monotonic within a thread but may interleave across threads.
 *
 * <p>Used by notification store implementations for stable cursor pagination —
 * {@code (created_at DESC, id DESC)} ordering is deterministic.
 */
public final class UUIDv7 {

    private static final ThreadLocal<State> THREAD_STATE = ThreadLocal.withInitial(State::new);

    private UUIDv7() {
        // Utility class — no instances
    }

    private static class State {
        long lastTimestamp = 0;
        int sequence = 0;
    }

    /**
     * Generate a new UUID v7 with current timestamp.
     *
     * @return UUID v7 with millisecond-precision timestamp
     */
    public static String generate() {
        return generate(Instant.now());
    }

    /**
     * Reset thread-local state. Used by tests to ensure clean state between test methods.
     */
    public static void resetState() {
        THREAD_STATE.remove();
    }

    /**
     * Generate a UUID v7 with specified timestamp. Exposed for testing.
     *
     * <p>Handles sequence overflow and clock regression:
     * <ul>
     *   <li>When sequence exceeds 4095 (0xFFF), advances timestamp by 1ms and resets sequence to 0.</li>
     *   <li>When clock goes backward (timestampMs &lt; lastTimestamp), treats it as same-millisecond case,
     *       incrementing sequence and wrapping if needed. Ensures UUIDs remain monotonic even during
     *       clock regression.</li>
     * </ul>
     *
     * @param instant timestamp to embed in the UUID
     * @return UUID v7 with the given timestamp
     */
    static String generate(Instant instant) {
        long timestampMs = instant.toEpochMilli();
        State state = THREAD_STATE.get();

        if (timestampMs <= state.lastTimestamp) {
            timestampMs = state.lastTimestamp;
            state.sequence++;
            if (state.sequence > 0xFFF) {
                timestampMs++;
                state.lastTimestamp = timestampMs;
                state.sequence = 0;
            }
        } else {
            state.lastTimestamp = timestampMs;
            state.sequence = 0;
        }
        int sequence = state.sequence;

        // 48-bit timestamp (milliseconds since epoch)
        long mostSigBits = timestampMs << 16;

        // 12-bit sequence (monotonic within same millisecond)
        mostSigBits |= sequence;

        // Set version 7 (0111 in bits 48-51)
        mostSigBits &= ~(0xF000L);  // Clear version bits
        mostSigBits |= 0x7000L;     // Set to version 7

        // 62-bit random rand_b (2 bits reserved for variant)
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long leastSigBits = random.nextLong();

        // Set variant to RFC 4122 (10 in bits 64-65)
        leastSigBits &= ~(0xC000_0000_0000_0000L);  // Clear variant bits
        leastSigBits |= 0x8000_0000_0000_0000L;     // Set to RFC 4122 variant (10)

        return new UUID(mostSigBits, leastSigBits).toString();
    }
}
