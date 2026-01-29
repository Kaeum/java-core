package com.example.concurrency.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future & Callable 기초
 *
 * Runnable vs Callable:
 * - Runnable: void run() - 반환값 없음, checked exception 못 던짐
 * - Callable: V call() throws Exception - 반환값 있음, 예외 던질 수 있음
 *
 * Future:
 * - 비동기 작업의 "미래 결과"를 담는 핸들러
 * - get(): 결과가 준비될 때까지 블로킹
 * - get(timeout): 타임아웃 지정 가능
 * - cancel(): 작업 취소 시도
 * - isDone(), isCancelled(): 상태 확인
 *
 * 면접 질문: "Future.get()의 문제점은?"
 * - 블로킹이라 여러 Future를 효율적으로 처리하기 어려움
 * - 콜백 불가능 (완료 시 자동 실행되는 로직 못 붙임)
 * - 대안: CompletableFuture
 */
public class FutureBasics {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Future & Callable 기초 ===\n");

        demonstrateCallable();
        demonstrateFutureGet();
        demonstrateFutureTimeout();
        demonstrateFutureCancel();
        demonstrateMultipleFutures();
    }

    /**
     * Callable: 결과를 반환하는 작업
     */
    private static void demonstrateCallable() throws Exception {
        System.out.println("[Callable - 결과 반환]");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Runnable: 반환값 없음
        Runnable runnableTask = () -> {
            System.out.println("  Runnable 실행");
        };
        Future<?> runnableFuture = executor.submit(runnableTask);
        System.out.println("  결과: " + runnableFuture.get());
        // Callable: 반환값 있음
        Callable<Integer> callableTask = () -> {
            System.out.println("  Callable 실행");
            return 1 + 1;
        };
        Future<Integer> callableFuture = executor.submit(callableTask);
        Integer result = callableFuture.get();
        System.out.println("  결과: " + result);

        executor.shutdown();
        System.out.println();
    }

    /**
     * Future.get(): 블로킹 대기
     */
    private static void demonstrateFutureGet() throws Exception {
        System.out.println("[Future.get() - 블로킹 대기]");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        long start = System.currentTimeMillis();

        Future<String> future = executor.submit(() -> {
            Thread.sleep(1000);
            return "작업 완료";
        });

        System.out.println("  작업 제출 완료, get() 호출...");
        String result = future.get();  // 여기서 1초 블로킹
        System.out.println("  결과: " + result);
        System.out.println("  소요 시간: " + (System.currentTimeMillis() - start) + "ms");

        executor.shutdown();
        System.out.println();
    }

    /**
     * Future.get(timeout): 타임아웃 지정
     */
    private static void demonstrateFutureTimeout() {
        System.out.println("[Future.get(timeout) - 타임아웃]");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            Thread.sleep(5000);  // 5초 걸리는 작업
            return "완료";
        });

        try {
            String result = future.get(1, TimeUnit.SECONDS);  // 1초만 대기
            System.out.println("  결과: " + result);
        } catch (TimeoutException e) {
            System.out.println("  TimeoutException: 1초 내에 완료 안 됨");
            future.cancel(true);  // 타임아웃 시 취소
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        executor.shutdownNow();
        System.out.println();
    }

    /**
     * Future.cancel(): 작업 취소
     */
    private static void demonstrateFutureCancel() throws Exception {
        System.out.println("[Future.cancel() - 작업 취소]");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    System.out.println("  작업 진행 중... " + i);
                    Thread.sleep(200);
                }
                return "완료";
            } catch (InterruptedException e) {
                System.out.println("  InterruptedException 발생 - 작업 중단됨");
                Thread.currentThread().interrupt();
                return "중단됨";
            }
        });

        Thread.sleep(500);  // 잠시 실행되게 둔 후

        // cancel(true): 실행 중인 작업도 interrupt
        // cancel(false): 시작 안 된 작업만 취소
        boolean cancelled = future.cancel(true);
        System.out.println("  cancel 호출 결과: " + cancelled);
        System.out.println("  isCancelled: " + future.isCancelled());
        System.out.println("  isDone: " + future.isDone());  // 취소되어도 done은 true

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * 여러 Future 처리의 문제점
     * - 순차적으로 get() 해야 해서 비효율적
     * - 먼저 끝난 작업을 먼저 처리 불가
     */
    private static void demonstrateMultipleFutures() throws Exception {
        System.out.println("[여러 Future 처리의 문제점]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Future<String>> futures = new ArrayList<>();

        // 작업 시간이 다른 3개 작업
        futures.add(executor.submit(() -> { Thread.sleep(3000); return "작업A(3초)"; }));
        futures.add(executor.submit(() -> { Thread.sleep(1000); return "작업B(1초)"; }));
        futures.add(executor.submit(() -> { Thread.sleep(2000); return "작업C(2초)"; }));

        long start = System.currentTimeMillis();

        // 문제: 순서대로 get() 해야 함
        // 작업B가 1초 만에 끝나도, 작업A.get()에서 3초 대기
        for (int i = 0; i < futures.size(); i++) {
            String result = futures.get(i).get();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  " + result + " 수신 (경과: " + elapsed + "ms)");
        }

        System.out.println("  -> 작업B가 먼저 끝났지만, 작업A 대기 중에 처리 못함");
        System.out.println("  -> 해결: CompletableFuture 또는 ExecutorCompletionService");

        executor.shutdown();
        System.out.println();
    }
}
