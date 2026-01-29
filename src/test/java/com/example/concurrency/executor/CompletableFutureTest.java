package com.example.concurrency.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletableFutureTest {

    // === 생성 ===

    @Test
    @DisplayName("completedFuture는 이미 완료된 Future를 생성한다")
    void completedFutureIsAlreadyDone() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("done");

        assertTrue(cf.isDone());
        assertEquals("done", cf.join());
    }

    @Test
    @DisplayName("supplyAsync는 결과를 반환하는 비동기 작업을 생성한다")
    void supplyAsyncReturnsResult() throws Exception {
        CompletableFuture<Integer> cf = CompletableFuture.supplyAsync(() -> 1 + 1);

        assertEquals(2, cf.get());
    }

    @Test
    @DisplayName("runAsync는 결과 없는 비동기 작업을 생성한다")
    void runAsyncReturnsVoid() throws Exception {
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            // side effect only
        });

        assertNull(cf.get());
    }

    // === 콜백 체이닝 ===

    @Test
    @DisplayName("thenApply는 결과를 변환한다")
    void thenApplyTransformsResult() throws Exception {
        CompletableFuture<Integer> cf = CompletableFuture
            .supplyAsync(() -> "hello")
            .thenApply(String::length);

        assertEquals(5, cf.get());
    }

    @Test
    @DisplayName("thenAccept는 결과를 소비하고 Void를 반환한다")
    void thenAcceptConsumesResult() throws Exception {
        StringBuilder sb = new StringBuilder();

        CompletableFuture<Void> cf = CompletableFuture
            .supplyAsync(() -> "consumed")
            .thenAccept(sb::append);

        cf.get();
        assertEquals("consumed", sb.toString());
    }

    @Test
    @DisplayName("thenRun은 결과와 무관하게 실행된다")
    void thenRunIgnoresResult() throws Exception {
        StringBuilder sb = new StringBuilder();

        CompletableFuture<Void> cf = CompletableFuture
            .supplyAsync(() -> "ignored")
            .thenRun(() -> sb.append("executed"));

        cf.get();
        assertEquals("executed", sb.toString());
    }

    @Test
    @DisplayName("여러 thenApply를 체이닝할 수 있다")
    void multipleThenApplyCanBeChained() throws Exception {
        Integer result = CompletableFuture
            .supplyAsync(() -> 1)
            .thenApply(n -> n + 1)  // 2
            .thenApply(n -> n * 3)  // 6
            .thenApply(n -> n - 1)  // 5
            .get();

        assertEquals(5, result);
    }

    // === 예외 처리 ===

    @Test
    @DisplayName("exceptionally는 예외 발생 시 대체값을 반환한다")
    void exceptionallyProvidesFallback() throws Exception {
        String result = CompletableFuture
            .<String>supplyAsync(() -> {
                throw new RuntimeException("error");
            })
            .exceptionally(ex -> "fallback")
            .get();

        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("exceptionally는 정상 완료 시 스킵된다")
    void exceptionallyIsSkippedOnSuccess() throws Exception {
        String result = CompletableFuture
            .supplyAsync(() -> "success")
            .exceptionally(ex -> "fallback")
            .get();

        assertEquals("success", result);
    }

    @Test
    @DisplayName("handle은 성공과 실패 모두 처리한다")
    void handleProcessesBothSuccessAndFailure() throws Exception {
        // 성공 케이스
        String success = CompletableFuture
            .supplyAsync(() -> "ok")
            .handle((r, ex) -> ex == null ? r : "error")
            .get();
        assertEquals("ok", success);

        // 실패 케이스
        String failure = CompletableFuture
            .<String>supplyAsync(() -> { throw new RuntimeException(); })
            .handle((r, ex) -> ex == null ? r : "error")
            .get();
        assertEquals("error", failure);
    }

    @Test
    @DisplayName("whenComplete는 결과를 변경하지 않는다")
    void whenCompleteDoesNotChangeResult() throws Exception {
        String result = CompletableFuture
            .supplyAsync(() -> "original")
            .whenComplete((r, ex) -> {
                // 로깅 등 부수효과만
            })
            .get();

        assertEquals("original", result);
    }

    @Test
    @DisplayName("예외 처리 없이 예외 발생 시 ExecutionException으로 래핑된다")
    void unhandledExceptionWrappedInExecutionException() {
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("test");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, cf::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // === 작업 조합 ===

    @Test
    @DisplayName("thenCompose는 중첩된 CompletableFuture를 평탄화한다")
    void thenComposeFlattensFutures() throws Exception {
        CompletableFuture<String> cf = CompletableFuture
            .supplyAsync(() -> 1)
            .thenCompose(n -> CompletableFuture.supplyAsync(() -> "number: " + n));

        assertEquals("number: 1", cf.get());
    }

    @Test
    @DisplayName("thenCombine은 두 독립 작업의 결과를 합친다")
    void thenCombineMergesTwoResults() throws Exception {
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> "Hello");
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> "World");

        String result = cf1.thenCombine(cf2, (a, b) -> a + " " + b).get();

        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("allOf는 모든 작업이 완료될 때까지 대기한다")
    void allOfWaitsForAllFutures() throws Exception {
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "a";
        });
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "b";
        });

        CompletableFuture.allOf(cf1, cf2).get();

        assertTrue(cf1.isDone());
        assertTrue(cf2.isDone());
        assertEquals("a", cf1.get());
        assertEquals("b", cf2.get());
    }

    @Test
    @DisplayName("anyOf는 가장 먼저 완료된 결과를 반환한다")
    void anyOfReturnsFirstCompleted() throws Exception {
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "slow";
        });
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "fast";
        });

        Object result = CompletableFuture.anyOf(slow, fast).get();

        assertEquals("fast", result);
    }

    // === join vs get ===

    @Test
    @DisplayName("join은 checked exception 대신 CompletionException을 던진다")
    void joinThrowsCompletionException() {
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("error");
        });

        // get()은 ExecutionException (checked)
        assertThrows(ExecutionException.class, cf::get);

        // join()은 CompletionException (unchecked)
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("error");
        });
        assertThrows(CompletionException.class, cf2::join);
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}