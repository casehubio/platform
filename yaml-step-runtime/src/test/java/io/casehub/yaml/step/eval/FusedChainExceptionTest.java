package io.casehub.yaml.step.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FusedChainExceptionTest {

    @Test
    void capturesChainDiagnostics() {
        var cause = new RuntimeException("denoise failed");
        var ex = new FusedChainException("denoise", "audio-denoise", 1, "transcript", cause);
        assertEquals("denoise", ex.failedStepName());
        assertEquals("audio-denoise", ex.failedActionName());
        assertEquals(1, ex.failedStepIndex());
        assertEquals("transcript", ex.chainName());
        assertSame(cause, ex.getCause());
        assertTrue(ex.getMessage().contains("denoise"));
        assertTrue(ex.getMessage().contains("audio-denoise"));
        assertTrue(ex.getMessage().contains("transcript"));
    }
}
