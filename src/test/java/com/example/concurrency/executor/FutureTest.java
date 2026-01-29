package com.example.concurrency.executor;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FutureTest {

    @Test
    @DisplayName("Callable은 결과를 반환한다")
    void callableReturnsResult() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Integer> future = executor.submit(() -> 1 + 1);
        Integer result = future.get();

        assertEquals(2, result);
        executor.shutdown();
    }

    @Test
    @DisplayName("Callable에서 발생한 예외는 ExecutionException으로 래핑된다")
    void callableExceptionWrappedInExecutionException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            throw new IllegalArgumentException("test error");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertEquals("test error", ex.getCause().getMessage());

        executor.shutdown();
    }

    @Test
    @DisplayName("Future.get(timeout)은 시간 초과 시 TimeoutException을 던진다")
    void getWithTimeoutThrowsTimeoutException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            Thread.sleep(5000);
            return "done";
        });

        assertThrows(TimeoutException.class, () -> {
            future.get(100, TimeUnit.MILLISECONDS);
        });

        future.cancel(true);
        executor.shutdown();
    }

    @Test
    @DisplayName("Future.cancel(true)는 실행 중인 작업을 interrupt한다")
    void cancelInterruptsRunningTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            try {
                Thread.sleep(5000);
                return "completed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        });

        Thread.sleep(100);  // 작업 시작 대기
        boolean cancelled = future.cancel(true);

        assertTrue(cancelled);
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());  // 취소되어도 done은 true

        executor.shutdown();
    }

    @Test
    @DisplayName("취소된 Future에 get() 호출 시 CancellationException")
    void getCancelledFutureThrowsCancellationException() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            Thread.sleep(5000);
            return "done";
        });

        Thread.sleep(50);
        future.cancel(true);

        assertThrows(CancellationException.class, future::get);

        executor.shutdown();
    }

    @Test
    @DisplayName("isDone()은 완료/취소/예외 모두에서 true")
    void isDoneReturnsTrueAfterCompletion() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 정상 완료
        Future<String> success = executor.submit(() -> "ok");
        success.get();
        assertTrue(success.isDone());

        // 예외 발생
        Future<String> error = executor.submit(() -> {
            throw new RuntimeException("fail");
        });
        assertThrows(ExecutionException.class, error::get);
        assertTrue(error.isDone());

        // 취소
        Future<String> cancelled = executor.submit(() -> {
            Thread.sleep(5000);
            return "done";
        });
        Thread.sleep(50);
        cancelled.cancel(true);
        assertTrue(cancelled.isDone());

        executor.shutdown();
    }
}
