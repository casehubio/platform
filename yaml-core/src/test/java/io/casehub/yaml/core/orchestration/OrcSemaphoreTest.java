package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcSemaphoreTest {

    @Test
    void acquireAndRelease_basic() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("test", 2);
        assertThat(sem.availablePermits()).isEqualTo(2);
        sem.acquire();
        assertThat(sem.availablePermits()).isEqualTo(1);
        sem.release();
        assertThat(sem.availablePermits()).isEqualTo(2);
    }

    @Test
    void tryAcquireTimeout_returnsFalse() throws Exception {
        var sem = new DefaultOrcSemaphore("test", 1);
        sem.acquire();
        var result = new java.util.concurrent.atomic.AtomicBoolean(true);
        var t = Thread.ofVirtual().name("tryAcquire-thread").start(() -> {
            try {
                result.set(sem.tryAcquire(50, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.join();
        assertThat(result.get()).isFalse();
        sem.release();
    }

    @Test
    void availablePermits_tracksCorrectly() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("test", 3);
        sem.acquire();
        sem.acquire();
        assertThat(sem.availablePermits()).isEqualTo(1);
        sem.release();
        assertThat(sem.availablePermits()).isEqualTo(2);
        sem.release();
        assertThat(sem.availablePermits()).isEqualTo(3);
    }

    @Test
    void singlePermit_reentrancy_throws() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("mutex", 1);
        sem.acquire();
        assertThatThrownBy(sem::acquire)
                .isInstanceOf(SemaphoreReentrancyException.class)
                .hasMessageContaining("mutex");
        sem.release();
    }

    @Test
    void multiPermit_noReentrancyTracking() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("pool", 3);
        sem.acquire();
        sem.acquire();
        assertThat(sem.availablePermits()).isEqualTo(1);
        sem.release();
        sem.release();
    }

    @Test
    void mutexSugar_equivalentToPermitsOne() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("portfolio-state", 1);
        sem.acquire();
        assertThat(sem.availablePermits()).isEqualTo(0);
        sem.release();
        assertThat(sem.availablePermits()).isEqualTo(1);
    }
}
