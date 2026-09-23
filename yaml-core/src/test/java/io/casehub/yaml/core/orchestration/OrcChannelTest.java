package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcChannelTest {

    @Test
    void sendAndReceive_basic() throws InterruptedException {
        var ch = new DefaultOrcChannel<String>("test");
        ch.send("hello");
        assertThat(ch.receive()).isEqualTo("hello");
    }

    @Test
    void blocksOnFullBounded() throws Exception {
        var ch = new DefaultOrcChannel<String>("bounded", 1);
        ch.send("first");
        var blocked = new java.util.concurrent.atomic.AtomicBoolean(true);
        var t = Thread.ofVirtual().name("sender").start(() -> {
            try {
                ch.send("second");
                blocked.set(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(50);
        assertThat(blocked.get()).isTrue();
        ch.receive();
        t.join(1000);
        assertThat(blocked.get()).isFalse();
    }

    @Test
    void blocksOnEmptyReceive() throws Exception {
        var ch = new DefaultOrcChannel<String>("empty");
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var t = Thread.ofVirtual().name("receiver").start(() -> {
            try {
                received.set(ch.receive());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(50);
        assertThat(received.get()).isNull();
        ch.send("arrived");
        t.join(1000);
        assertThat(received.get()).isEqualTo("arrived");
    }

    @Test
    void close_drainThenClosed() throws InterruptedException {
        var ch = new DefaultOrcChannel<String>("drain");
        ch.send("a");
        ch.send("b");
        ch.close();
        assertThat(ch.receive()).isEqualTo("a");
        assertThat(ch.receive()).isEqualTo("b");
        assertThat(ch.receive()).isNull();
    }

    @Test
    void unbounded_neverBlocksOnSend() throws InterruptedException {
        var ch = new DefaultOrcChannel<Integer>("unbounded");
        for (int i = 0; i < 1000; i++) {
            ch.send(i);
        }
        assertThat(ch.isEmpty()).isFalse();
    }

    @Test
    void sendOnClosedChannel_throwsChannelClosedException() {
        var ch = new DefaultOrcChannel<String>("closed");
        ch.close();
        assertThatThrownBy(() -> ch.send("data"))
                .isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void errorClose_producerFailure_consumersGetException() throws InterruptedException {
        var ch = new DefaultOrcChannel<String>("error");
        ch.send("ok-item");
        ch.close(new RuntimeException("producer died"));
        assertThat(ch.receive()).isEqualTo("ok-item");
        assertThatThrownBy(ch::receive)
                .isInstanceOf(ChannelClosedException.class)
                .hasMessageContaining("producer died");
    }

    @Test
    void errorClose_drainsRemainingBeforeError() throws InterruptedException {
        var ch = new DefaultOrcChannel<String>("drain-error");
        ch.send("a");
        ch.send("b");
        ch.close(new RuntimeException("fail"));
        assertThat(ch.receive()).isEqualTo("a");
        assertThat(ch.receive()).isEqualTo("b");
        assertThatThrownBy(ch::receive)
                .isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void isErrorClosed_reflectsState() {
        var ch = new DefaultOrcChannel<String>("state");
        assertThat(ch.isErrorClosed()).isFalse();
        ch.close();
        assertThat(ch.isErrorClosed()).isFalse();
        var ch2 = new DefaultOrcChannel<String>("state2");
        ch2.close(new RuntimeException("err"));
        assertThat(ch2.isErrorClosed()).isTrue();
        assertThat(ch2.closeError()).hasMessage("err");
    }
}
