package com.example.concurrency.executor;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutorTest {

    @Test
    @DisplayName("FixedThreadPool은 지정한 수만큼만 스레드를 생성한다")
    void fixedThreadPoolLimitsThreadCount() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger current = new AtomicInteger(0);

        int taskCount = 20;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                int running = current.incrementAndGet();
                maxConcurrent.updateAndGet(prev -> Math.max(prev, running));
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    current.decrementAndGet();
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(maxConcurrent.get() <= 3,
                "동시 실행 스레드 수가 3 이하여야 한다. 실제: " + maxConcurrent.get());
    }

    @Test
    @DisplayName("SingleThreadExecutor는 작업을 순서대로 실행한다")
    void singleThreadExecutorMaintainsOrder() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        StringBuilder order = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 1; i <= 5; i++) {
            final int num = i;
            executor.submit(() -> {
                order.append(num);
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("12345", order.toString());
    }

    @Test
    @DisplayName("shutdown 후 새 작업 제출 시 RejectedExecutionException")
    void shutdownRejectsNewTasks() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.shutdown();

        assertThrows(RejectedExecutionException.class, () -> {
            executor.submit(() -> {});
        });
    }

    @Test
    @DisplayName("shutdownNow는 대기 중인 작업을 반환한다")
    void shutdownNowReturnsWaitingTasks() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(1);

        // 1개 스레드가 긴 작업 실행 중
        executor.submit(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 나머지 작업은 큐에 대기
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {});
        }

        Thread.sleep(50);

        executor.awaitTermination(1, TimeUnit.SECONDS);
        List<Runnable> notExecuted = executor.shutdownNow();

        assertTrue(notExecuted.size() == 5,
                "대기 중인 작업이 반환되어야 한다. 반환된 수: " + notExecuted.size());
    }

    @Test
    @DisplayName("ThreadPoolExecutor: 큐 초과 시 maxPoolSize까지 확장")
    void threadPoolExpandsToMaxWhenQueueFull() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2)
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(6);

        for (int i = 0; i < 6; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Thread.sleep(100);
        assertEquals(4, executor.getPoolSize(),
                "큐가 가득 차면 maxPoolSize까지 확장");

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    @DisplayName("ThreadPoolExecutor: max 초과 시 RejectedExecutionException")
    void threadPoolRejectsWhenMaxExceeded() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1)
        );

        CountDownLatch hold = new CountDownLatch(1);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThrows(RejectedExecutionException.class, () -> {
            executor.submit(() -> {});
        });

        hold.countDown();
        executor.shutdown();
    }
}
