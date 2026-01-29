package com.example.concurrency.executor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvokeTest {

    @Test
    @DisplayName("invokeAll은 모든 작업 완료 후 Future 리스트를 반환한다")
    void invokeAllWaitsForAllTasks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<Integer>> tasks = Arrays.asList(
            () -> { Thread.sleep(100); return 1; },
            () -> { Thread.sleep(200); return 2; },
            () -> { Thread.sleep(150); return 3; }
        );

        List<Future<Integer>> futures = executor.invokeAll(tasks);

        // 모든 Future가 완료 상태
        for (Future<Integer> f : futures) {
            assertTrue(f.isDone());
        }

        // 제출 순서대로 결과
        assertEquals(1, futures.get(0).get());
        assertEquals(2, futures.get(1).get());
        assertEquals(3, futures.get(2).get());

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAll(timeout)은 시간 초과된 작업을 취소한다")
    void invokeAllWithTimeoutCancelsUnfinishedTasks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Callable<String>> tasks = Arrays.asList(
            () -> { Thread.sleep(100); return "fast"; },
            () -> { Thread.sleep(5000); return "slow"; }  // 타임아웃 초과
        );

        List<Future<String>> futures = executor.invokeAll(tasks, 500, TimeUnit.MILLISECONDS);

        // 빠른 작업은 완료
        assertEquals("fast", futures.get(0).get());

        // 느린 작업은 취소됨
        assertTrue(futures.get(1).isCancelled());
        assertThrows(CancellationException.class, () -> futures.get(1).get());

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAll에서 예외가 발생해도 다른 작업은 계속 실행된다")
    void invokeAllContinuesEvenWhenTaskFails() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            () -> "success1",
            () -> { throw new RuntimeException("fail"); },
            () -> "success2"
        );

        List<Future<String>> futures = executor.invokeAll(tasks);

        // 성공한 작업
        assertEquals("success1", futures.get(0).get());
        assertEquals("success2", futures.get(2).get());

        // 실패한 작업은 ExecutionException
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> futures.get(1).get());
        assertEquals("fail", ex.getCause().getMessage());

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAny는 가장 먼저 성공한 결과를 반환한다")
    void invokeAnyReturnsFirstSuccessfulResult() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<Integer>> tasks = Arrays.asList(
            () -> { Thread.sleep(500); return 1; },
            () -> { Thread.sleep(100); return 2; },  // 가장 빠름
            () -> { Thread.sleep(300); return 3; }
        );

        long start = System.currentTimeMillis();
        Integer result = executor.invokeAny(tasks);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(2, result);
        assertTrue(elapsed < 300);  // 100ms 작업이 반환되므로 300ms 이전에 완료

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAny는 일부 작업이 실패해도 성공한 결과가 있으면 반환한다")
    void invokeAnyReturnsSuccessEvenIfSomeFail() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            () -> { throw new RuntimeException("fail1"); },
            () -> { Thread.sleep(100); return "success"; },
            () -> { throw new RuntimeException("fail2"); }
        );

        String result = executor.invokeAny(tasks);
        assertEquals("success", result);

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAny는 모든 작업이 실패하면 ExecutionException을 던진다")
    void invokeAnyThrowsWhenAllTasksFail() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Callable<String>> tasks = Arrays.asList(
            () -> { throw new RuntimeException("error1"); },
            () -> { throw new RuntimeException("error2"); }
        );

        assertThrows(ExecutionException.class, () -> executor.invokeAny(tasks));

        executor.shutdown();
    }

    @Test
    @DisplayName("invokeAny는 빈 리스트에 대해 IllegalArgumentException을 던진다")
    void invokeAnyThrowsOnEmptyList() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        List<Callable<String>> emptyTasks = Arrays.asList();

        assertThrows(IllegalArgumentException.class, () -> executor.invokeAny(emptyTasks));

        executor.shutdown();
    }
}