package codex.ir.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebouncerTest {

    private final Debouncer debouncer = new Debouncer();

    @AfterEach
    void tearDown() {
        debouncer.close();
    }

    @Test
    void shouldRejectNullKey() {
        assertThrows(NullPointerException.class,
                () -> debouncer.debounce(null, () -> {
                }, 10, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRejectNullRunnable() {
        assertThrows(NullPointerException.class,
                () -> debouncer.debounce("key", null, 10, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRejectNullTimeUnit() {
        assertThrows(NullPointerException.class,
                () -> debouncer.debounce("key", () -> {
                }, 10, null));
    }

    @Test
    void shouldRejectNegativeDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> debouncer.debounce("key", () -> {
                }, -1, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRejectDebounceAfterClose() {
        debouncer.close();

        assertThrows(IllegalStateException.class,
                () -> debouncer.debounce("key", () -> {
                }, 10, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRunOnlyLatestTaskForSameKey() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);

        debouncer.debounce("same-key", counter::incrementAndGet, 120, TimeUnit.MILLISECONDS);
        Thread.sleep(30);
        debouncer.debounce("same-key", () -> {
            counter.addAndGet(10);
            latch.countDown();
        }, 120, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Latest debounced task should execute");
        assertEquals(10, counter.get(), "Only the latest task for the same key should run");
    }

    @Test
    void shouldRunTasksForDifferentKeysIndependently() throws InterruptedException {
        final List<String> executed = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(2);

        debouncer.debounce("key-a", () -> {
            executed.add("A");
            latch.countDown();
        }, 50, TimeUnit.MILLISECONDS);

        debouncer.debounce("key-b", () -> {
            executed.add("B");
            latch.countDown();
        }, 50, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Tasks for different keys should execute independently");
        assertEquals(2, executed.size());
        assertTrue(executed.contains("A"));
        assertTrue(executed.contains("B"));
    }

    @Test
    void shouldAllowImmediateExecutionAndSubsequentReschedule() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(2);

        debouncer.debounce("zero-delay", () -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 0, TimeUnit.MILLISECONDS);

        debouncer.debounce("zero-delay", () -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 20, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Immediate and subsequent debounced executions should complete");
        assertEquals(2, counter.get(), "The key should still be reusable after an immediate execution");
    }

    @Test
    void shouldCancelSupersededTaskBeforeDelayExpires() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);

        debouncer.debounce("replaceable", () -> counter.addAndGet(100), 200, TimeUnit.MILLISECONDS);
        Thread.sleep(25);
        debouncer.debounce("replaceable", () -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 60, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Replacement task should execute");
        Thread.sleep(250);
        assertEquals(1, counter.get(), "Superseded task should not execute after being cancelled");
    }
}
