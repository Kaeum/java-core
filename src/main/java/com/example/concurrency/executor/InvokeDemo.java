package com.example.concurrency.executor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * invokeAll() & invokeAny() - 여러 Callable 일괄 실행
 *
 * invokeAll():
 * - 모든 작업이 완료될 때까지 블로킹
 * - 모든 Future를 List로 반환
 * - 타임아웃 지정 가능 (시간 초과 시 미완료 작업 취소)
 *
 * invokeAny():
 * - 하나라도 성공하면 즉시 반환
 * - 나머지 작업은 자동 취소
 * - 모두 실패하면 ExecutionException
 *
 * 생각해볼 점: "invokeAll vs invokeAny 차이점?"
 * - invokeAll: 전체 결과 필요 (ex: 여러 DB에서 데이터 수집)
 * - invokeAny: 하나만 있으면 됨 (ex: 여러 서버에 같은 요청, 가장 빠른 응답 사용)
 */
public class InvokeDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== invokeAll & invokeAny ===\n");

        demonstrateInvokeAll();
        demonstrateInvokeAllWithTimeout();
        demonstrateInvokeAny();
        demonstrateInvokeAnyAllFail();
    }

    /**
     * invokeAll(): 모든 작업 완료 대기
     */
    private static void demonstrateInvokeAll() throws Exception {
        System.out.println("[invokeAll - 모든 작업 완료 대기]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            createTask("작업A", 1000),
            createTask("작업B", 2000),
            createTask("작업C", 1500)
        );

        long start = System.currentTimeMillis();

        // 모든 작업이 완료될 때까지 블로킹
        List<Future<String>> futures = executor.invokeAll(tasks);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  invokeAll 반환 (소요: " + elapsed + "ms)");

        // 이 시점에서 모든 Future는 isDone() == true
        for (Future<String> f : futures) {
            System.out.println("  결과: " + f.get() + ", isDone: " + f.isDone());
        }

        executor.shutdown();
        System.out.println();
    }

    /**
     * invokeAll(timeout): 타임아웃 지정
     * - 시간 초과 시 미완료 작업은 취소됨
     */
    private static void demonstrateInvokeAllWithTimeout() throws Exception {
        System.out.println("[invokeAll(timeout) - 타임아웃 시 미완료 작업 취소]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            createTask("빠른작업", 500),
            createTask("느린작업", 5000)  // 5초 걸림
        );

        // 1.5초 타임아웃
        List<Future<String>> futures = executor.invokeAll(tasks, 1500, TimeUnit.MILLISECONDS);

        for (Future<String> f : futures) {
            if (f.isCancelled()) {
                System.out.println("  [취소됨] 타임아웃으로 작업 취소");
            } else {
                try {
                    System.out.println("  결과: " + f.get());
                } catch (ExecutionException e) {
                    System.out.println("  [실패] " + e.getCause().getMessage());
                }
            }
        }

        executor.shutdown();
        System.out.println();
    }

    /**
     * invokeAny(): 하나라도 성공하면 즉시 반환
     */
    private static void demonstrateInvokeAny() throws Exception {
        System.out.println("[invokeAny - 가장 빠른 결과 반환]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> tasks = Arrays.asList(
            createTask("서버A", 3000),
            createTask("서버B", 1000),  // 가장 빠름
            createTask("서버C", 2000)
        );

        long start = System.currentTimeMillis();

        // 가장 먼저 완료된 결과 반환 (나머지는 취소)
        String result = executor.invokeAny(tasks);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  결과: " + result + " (소요: " + elapsed + "ms)");
        System.out.println("  -> 서버B가 1초 만에 응답, 나머지 작업은 취소됨");

        executor.shutdown();
        System.out.println();
    }

    /**
     * invokeAny(): 모두 실패하면 ExecutionException
     */
    private static void demonstrateInvokeAnyAllFail() throws Exception {
        System.out.println("[invokeAny - 모두 실패 시]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<String>> failingTasks = Arrays.asList(
            () -> { throw new RuntimeException("서버A 에러"); },
            () -> { throw new RuntimeException("서버B 에러"); },
            () -> { throw new RuntimeException("서버C 에러"); }
        );

        try {
            executor.invokeAny(failingTasks);
        } catch (ExecutionException e) {
            System.out.println("  모든 작업 실패: " + e.getCause().getMessage());
            System.out.println("  -> invokeAny는 하나라도 성공해야 결과 반환");
        }

        executor.shutdown();
        System.out.println();
    }

    private static Callable<String> createTask(String name, long sleepMs) {
        return () -> {
            Thread.sleep(sleepMs);
            return name + " 응답";
        };
    }
}