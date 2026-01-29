package com.example.concurrency.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ExecutorCompletionService - 먼저 끝난 작업부터 처리
 *
 * Future의 문제점:
 * - 여러 Future를 List로 관리하면, 순서대로 get() 해야 함
 * - 작업B가 먼저 끝나도, 작업A.get()에서 블로킹 중이면 처리 불가
 *
 * ExecutorCompletionService 해결책:
 * - 내부에 BlockingQueue를 가짐
 * - 작업이 완료되면 자동으로 큐에 Future가 들어감
 * - take() / poll()로 완료된 순서대로 가져올 수 있음
 *
 * 면접 질문: "여러 비동기 작업 중 먼저 끝난 것부터 처리하려면?"
 * - ExecutorCompletionService 사용
 * - 또는 CompletableFuture.anyOf() / CompletableFuture.allOf()
 */
public class CompletionServiceDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ExecutorCompletionService ===\n");

        demonstrateProblemWithFutureList();
        demonstrateCompletionService();
        demonstratePollVsTake();
    }

    /**
     * 문제 상황: Future List로 관리할 때
     */
    private static void demonstrateProblemWithFutureList() throws Exception {
        System.out.println("[문제: Future List 순서대로 get()]");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 이 방식은 FutureBasics에서 이미 다뤘음
        // 3초, 1초, 2초 작업인데 순서대로 get()하면:
        // - 작업A(3초) get() 대기 중에 이미 완료된 작업B, 작업C 처리 못함
        System.out.println("  작업A(3초), 작업B(1초), 작업C(2초) 제출");
        System.out.println("  순서대로 get() 하면 작업A 끝날 때까지 작업B, C 결과 못 받음");
        System.out.println("  -> ExecutorCompletionService로 해결\n");

        executor.shutdown();
    }

    /**
     * 해결책: ExecutorCompletionService
     */
    private static void demonstrateCompletionService() throws Exception {
        System.out.println("[해결: ExecutorCompletionService]");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

        // 작업 제출
        completionService.submit(createTask("작업A", 3000));
        completionService.submit(createTask("작업B", 1000));
        completionService.submit(createTask("작업C", 2000));

        long start = System.currentTimeMillis();

        // take(): 완료된 작업이 있을 때까지 블로킹, 완료되면 즉시 반환
        for (int i = 0; i < 3; i++) {
            Future<String> completed = completionService.take();  // 완료 순서대로!
            String result = completed.get();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  " + result + " 수신 (경과: " + elapsed + "ms)");
        }

        System.out.println("  -> 작업B(1초) -> 작업C(2초) -> 작업A(3초) 순서로 처리됨!");

        executor.shutdown();
        System.out.println();
    }

    /**
     * poll() vs take()
     * - take(): 완료된 작업 있을 때까지 블로킹
     * - poll(): 완료된 작업 없으면 즉시 null 반환 (non-blocking)
     * - poll(timeout): 지정 시간까지만 대기
     */
    private static void demonstratePollVsTake() throws Exception {
        System.out.println("[poll() vs take()]");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletionService<String> cs = new ExecutorCompletionService<>(executor);

        cs.submit(createTask("작업1", 2000));

        // poll() - 즉시 확인 (완료된 게 없으면 null)
        Future<String> immediate = cs.poll();
        System.out.println("  poll() 즉시 호출: " + immediate);  // null (아직 완료 안됨)

        // poll(timeout) - 1초만 대기
        Future<String> waitOne = cs.poll(1, TimeUnit.SECONDS);
        System.out.println("  poll(1초) 호출: " + waitOne);  // null (2초 걸리는 작업)

        // take() - 완료될 때까지 대기
        Future<String> blocking = cs.take();
        System.out.println("  take() 호출: " + blocking.get());  // 2초 후 반환

        System.out.println();
        System.out.println("  poll(): non-blocking, 바로 확인만 할 때");
        System.out.println("  poll(timeout): 일정 시간만 대기할 때");
        System.out.println("  take(): 반드시 결과가 필요할 때 (블로킹 허용)");

        executor.shutdown();
        System.out.println();
    }

    private static Callable<String> createTask(String name, long sleepMs) {
        return () -> {
            Thread.sleep(sleepMs);
            return name + " 완료";
        };
    }
}