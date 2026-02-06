package com.example.concurrency.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CompletableFuture 기초
 *
 * Future의 한계:
 * - get() 블로킹 -> 여러 작업 조합 어려움
 * - 콜백 불가 -> 완료 시 자동 실행 로직 못 붙임
 * - 예외 처리 불편 -> ExecutionException으로 래핑됨
 *
 * CompletableFuture 해결책:
 * - 논블로킹 콜백 체이닝 (thenApply, thenAccept, thenRun)
 * - 여러 작업 조합 (thenCompose, thenCombine, allOf, anyOf)
 * - 선언적 예외 처리 (exceptionally, handle)
 *
 * 생각해볼 점: "Future vs CompletableFuture 차이?"
 * - Future: 블로킹 get(), 콜백 불가, 수동 예외 처리
 * - CompletableFuture: 논블로킹 체이닝, 콜백 지원, 선언적 예외 처리, 작업 조합
 */
public class CompletableFutureBasics {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CompletableFuture 기초 ===\n");

        demonstrateCreation();
        demonstrateCallback();
        demonstrateThenApplyVsAsync();
    }

    /**
     * CompletableFuture 생성 방법
     */
    private static void demonstrateCreation() throws Exception {
        System.out.println("[생성 방법]");

        // 1. 이미 완료된 Future
        CompletableFuture<String> completed = CompletableFuture.completedFuture("이미 완료");
        System.out.println("  completedFuture: " + completed.get());

        // 2. supplyAsync: 결과 반환 (Supplier)
        CompletableFuture<String> supply = CompletableFuture.supplyAsync(() -> {
            System.out.println("  supplyAsync 실행 스레드: " + Thread.currentThread().getName());
            return "비동기 결과";
        });
        System.out.println("  supplyAsync: " + supply.get());

        // 3. runAsync: 결과 없음 (Runnable)
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> {
            System.out.println("  runAsync 실행 스레드: " + Thread.currentThread().getName());
        });
        run.get();  // Void 반환

        // 4. 커스텀 Executor 지정
        // 지정 안하면 보통 ForkJoinPool.commonPool을 쓰게 됨.
        // 특정 작업(I/O 등)은 commonPool을 쓰면 네트워크 이슈로 고갈 위험이 있으므로, 별도 풀 지정이 필수적.
        ExecutorService customExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "custom-thread");
            t.setDaemon(true);
            return t;
        });
        CompletableFuture<String> withExecutor = CompletableFuture.supplyAsync(
            () -> "커스텀 Executor: " + Thread.currentThread().getName(),
            customExecutor
        );
        System.out.println("  " + withExecutor.get());
        customExecutor.shutdown();

        System.out.println();
    }

    /**
     * 콜백 체이닝: thenApply, thenAccept, thenRun
     *
     * thenApply: 결과 변환 (Function) - map과 유사
     * thenAccept: 결과 소비 (Consumer) - forEach와 유사
     * thenRun: 결과 무시, 작업만 실행 (Runnable)
     */
    private static void demonstrateCallback() throws Exception {
        System.out.println("[콜백 체이닝]");

        // thenApply: 결과 변환 (T -> U)
        CompletableFuture<Integer> lengthFuture = CompletableFuture
            .supplyAsync(() -> "Hello")
            .thenApply(s -> {
                System.out.println("  thenApply: " + s + " -> 길이 계산");
                return s.length();
            });
        System.out.println("  결과: " + lengthFuture.get());

        // thenAccept: 결과 소비 (T -> void)
        CompletableFuture<Void> acceptFuture = CompletableFuture
            .supplyAsync(() -> "World")
            .thenAccept(s -> System.out.println("  thenAccept 소비: " + s));
        acceptFuture.get();

        // thenRun: 결과 무시, 완료 후 실행
        CompletableFuture<Void> runFuture = CompletableFuture
            .supplyAsync(() -> "ignored")
            .thenRun(() -> System.out.println("  thenRun: 결과와 무관하게 실행"));
        runFuture.get();

        // 체이닝 예시
        System.out.println("\n  [체이닝 예시]");
        CompletableFuture
            .supplyAsync(() -> 10)
            .thenApply(n -> n * 2)          // 20
            .thenApply(n -> n + 5)          // 25
            .thenApply(n -> "결과: " + n)   // "결과: 25"
            .thenAccept(System.out::println);

        TimeUnit.MILLISECONDS.sleep(100);  // 비동기 완료 대기
        System.out.println();
    }

    /**
     * thenApply vs thenApplyAsync
     *
     * thenApply:
     * - 이전 작업이 아직 실행 중 -> 완료한 스레드가 이어서 실행
     * - 이전 작업이 이미 완료됨 -> 호출 스레드(main)에서 즉시 실행
     *
     * thenApplyAsync:
     * - 무조건 Executor(기본: ForkJoinPool)에 태스크로 제출
     * - "새 스레드 생성"이 아니라 "풀에서 가용 스레드 할당"
     * - 같은 worker 스레드가 재사용될 수 있음 (work-stealing)
     *
     * 생각해볼 점: "thenApply와 thenApplyAsync 차이?"
     * - thenApply: 동기적 실행 가능 (스레드 전환 없이 바로 실행될 수 있음)
     * - thenApplyAsync: 항상 비동기 (Executor에 제출, 스레드 풀에서 실행)
     * - 대부분 thenApply로 충분, 블로킹 I/O를 별도 풀에서 실행할 때 Async + 커스텀 Executor
     */
    private static void demonstrateThenApplyVsAsync() throws Exception {
        System.out.println("[thenApply vs thenApplyAsync]");

        // thenApply: 같은 스레드에서 실행될 수 있음
        CompletableFuture
            .supplyAsync(() -> {
                System.out.println("  supplyAsync: " + Thread.currentThread().getName());
                return "data";
            })
            .thenApply(s -> {
                System.out.println("  thenApply: " + Thread.currentThread().getName());
                return s.toUpperCase();
            })
            .get();

        // thenApplyAsync: 스레드 풀의 가용 워커에서 실행
        CompletableFuture
            .supplyAsync(() -> {
                System.out.println("  supplyAsync: " + Thread.currentThread().getName());
                return "data";
            })
            .thenApplyAsync(s -> {
                System.out.println("  thenApplyAsync: " + Thread.currentThread().getName());
                return s.toUpperCase();
            })
            .get();

        System.out.println();
    }
}