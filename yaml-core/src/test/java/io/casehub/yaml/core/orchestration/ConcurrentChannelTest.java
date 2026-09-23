package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentChannelTest {

    @Test
    void producerConsumer_safeUnderContention() throws InterruptedException {
        var ch = new DefaultOrcChannel<Integer>("contention", 10);
        int totalItems = 100;
        var received = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        var producersDone = new CountDownLatch(3);
        var consumersDone = new CountDownLatch(2);

        for (int p = 0; p < 3; p++) {
            int start = p * 34;
            int end = p == 2 ? totalItems : (p + 1) * 34;
            Thread.ofVirtual().name("producer-" + p).start(() -> {
                try {
                    for (int i = start; i < end; i++) {
                        ch.send(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
        }

        for (int c = 0; c < 2; c++) {
            Thread.ofVirtual().name("consumer-" + c).start(() -> {
                try {
                    while (true) {
                        Integer val = ch.receive(200, TimeUnit.MILLISECONDS);
                        if (val == null) break;
                        received.add(val);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    consumersDone.countDown();
                }
            });
        }

        producersDone.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);
        ch.close();
        consumersDone.await(5, TimeUnit.SECONDS);
        assertThat(received).hasSize(totalItems);
    }

    @Test
    void multipleConsumers_eachItemDeliveredOnce() throws InterruptedException {
        var ch = new DefaultOrcChannel<Integer>("once");
        int total = 50;
        var received = new AtomicInteger(0);
        var done = new CountDownLatch(3);

        for (int i = 0; i < total; i++) {
            ch.send(i);
        }
        ch.close();

        for (int c = 0; c < 3; c++) {
            Thread.ofVirtual().name("consumer-" + c).start(() -> {
                try {
                    while (true) {
                        Integer val = ch.receive(100, TimeUnit.MILLISECONDS);
                        if (val == null) break;
                        received.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(5, TimeUnit.SECONDS);
        assertThat(received.get()).isEqualTo(total);
    }
}
