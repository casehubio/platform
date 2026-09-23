package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrcPrimitiveTest {

    @Test
    void channelReleaseForClose_closesChannel() throws InterruptedException {
        var channel = new DefaultOrcChannel<String>("test");
        channel.send("data");
        channel.releaseForClose();
        assertThat(channel.isEmpty()).isFalse();
    }

    @Test
    void latchReleaseForClose_countsDownToZero() {
        var latch = new DefaultOrcLatch(3);
        latch.releaseForClose();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    void signalReleaseForClose_signalsIfUnsignalled() {
        var signal = new DefaultOrcSignal();
        assertThat(signal.isSignalled()).isFalse();
        signal.releaseForClose();
        assertThat(signal.isSignalled()).isTrue();
    }

    @Test
    void signalReleaseForClose_alreadySignalled_noOp() {
        var signal = new DefaultOrcSignal();
        signal.signal();
        signal.releaseForClose();
        assertThat(signal.isSignalled()).isTrue();
    }

    @Test
    void semaphoreReleaseForClose_shutsDown() {
        var sem = new DefaultOrcSemaphore("test", 1);
        sem.releaseForClose();
        assertThat(sem.availablePermits()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void stateMachineReleaseForClose_noOp() {
        var sm = DefaultOrcStateMachine.builder("test", TestState.class, TestState.A)
                .transition(TestState.A, TestState.B)
                .build();
        sm.releaseForClose();
        assertThat(sm.currentState()).isEqualTo(TestState.A);
    }

    @Test
    void scopeClose_usesPolymorphicDispatch() {
        var scope = new DefaultScenarioScope();
        var latch = scope.latch("test", 5);
        var signal = scope.signal("test-sig");
        scope.close();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(signal.isSignalled()).isTrue();
    }

    enum TestState { A, B }
}
