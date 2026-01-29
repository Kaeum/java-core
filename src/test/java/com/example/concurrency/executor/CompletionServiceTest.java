package com.example.concurrency.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletionServiceTest {

    @Test
    @DisplayName("take()는 완료된 순서대로 Future를 반환한다")
    void takeReturnsCompletedFuturesInOrder() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CompletionService<Integer> cs = new ExecutorCompletionService<>(executor);

        // 작업 시간: 300ms, 100ms, 200ms
        cs.submit(() -> { Thread.sleep(300); return 1; });
        cs.submit(() -> { Thread.sleep(100); return 2; });  // 가장 빠름
        cs.submit(() -> { Thread.sleep(200); return 3; });

        // 완료 순서: 2 -> 3 -> 1
        assertEquals(2, cs.take().get());
        assertEquals(3, cs.take().get());
        assertEquals(1, cs.take().get());

        executor.shutdown();
    }

    @Test
    @DisplayName("poll()은 완료된 작업이 없으면 null을 반환한다")
    void pollReturnsNullWhenNoCompletedTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletionService<String> cs = new ExecutorCompletionService<>(executor);

        cs.submit(() -> {
            Thread.sleep(1000);
            return "done";
        });

        // 즉시 poll - 아직 완료 안됨
        Future<String> result = cs.poll();
        assertNull(result);

        // 정리
        cs.take();  // 완료 대기
        executor.shutdown();
    }

    @Test
    @DisplayName("poll(timeout)은 지정 시간까지 대기한다")
    void pollWithTimeoutWaitsForDuration() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletionService<String> cs = new ExecutorCompletionService<>(executor);

        cs.submit(() -> {
            Thread.sleep(500);
            return "done";
        });

        long start = System.currentTimeMillis();

        // 200ms만 대기 - 작업 완료 안됨
        Future<String> first = cs.poll(200, TimeUnit.MILLISECONDS);
        long firstElapsed = System.currentTimeMillis() - start;

        assertNull(first);
        assertTrue(firstElapsed >= 180 && firstElapsed < 400);  // 약 200ms 대기

        // 500ms 더 대기 - 작업 완료됨
        Future<String> second = cs.poll(500, TimeUnit.MILLISECONDS);
        assertNotNull(second);
        assertEquals("done", second.get());

        executor.shutdown();
    }

    @Test
    @DisplayName("submit()은 Future를 반환하여 개별 추적도 가능하다")
    void submitReturnsFutureForIndividualTracking() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletionService<String> cs = new ExecutorCompletionService<>(executor);

        // submit()의 반환값으로 개별 작업 추적 가능
        Future<String> future = cs.submit(() -> "result");

        // take()로도, 반환된 Future로도 결과 접근 가능
        Future<String> completed = cs.take();

        // 같은 결과
        assertEquals("result", future.get());
        assertEquals("result", completed.get());

        executor.shutdown();
    }
}