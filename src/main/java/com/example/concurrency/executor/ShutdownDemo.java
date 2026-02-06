package com.example.concurrency.executor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ExecutorService 종료 전략
 *
 * 핵심:
 * - shutdown(): 새 작업 거부, 기존 작업은 완료까지 대기
 * - shutdownNow(): 실행 중인 작업에 interrupt, 대기 중인 작업 반환
 * - awaitTermination(): 지정 시간 동안 종료 대기
 *
 * 생각해볼 점: "ExecutorService를 안전하게 종료하는 방법은?"
 * - shutdown() -> awaitTermination() -> shutdownNow() 패턴 권장
 */
public class ShutdownDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ExecutorService 종료 전략 ===\n");

        demonstrateShutdown();
        demonstrateShutdownNow();
        demonstrateGracefulShutdown();
    }

    /**
     * shutdown(): 진행 중인 작업 완료 후 종료
     */
    private static void demonstrateShutdown() throws InterruptedException {
        System.out.println("[shutdown() - 우아한 종료]");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 1; i <= 5; i++) {
            final int taskNum = i;
            executor.submit(() -> {
                System.out.println("  Task " + taskNum + " 시작");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    System.out.println("  Task " + taskNum + " interrupted!");
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.println("  Task " + taskNum + " 완료");
            });
        }

        executor.shutdown();
        System.out.println("shutdown() 호출 - 새 작업 거부, 기존 작업은 계속 실행");

        try {
            executor.submit(() -> System.out.println("이건 실행 안 됨"));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            System.out.println("  -> 새 작업 거부됨 (RejectedExecutionException)");
        }

        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("모든 작업 완료 후 종료됨\n");
    }

    /**
     * shutdownNow(): 즉시 종료 시도
     */
    private static void demonstrateShutdownNow() throws InterruptedException {
        System.out.println("[shutdownNow() - 즉시 종료]");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 1; i <= 5; i++) {
            final int taskNum = i;
            executor.submit(() -> {
                try {
                    Thread.sleep(1000);
                    System.out.println("  Task " + taskNum + " 완료");
                } catch (InterruptedException e) {
                    System.out.println("  Task " + taskNum + " interrupted!");
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(100);

        System.out.println("shutdownNow() 호출");
        List<Runnable> notExecuted = executor.shutdownNow();
        System.out.println("  실행되지 못한 작업 수: " + notExecuted.size());

        executor.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("-> 실행 중인 작업은 interrupt, 대기 중인 작업은 반환\n");
    }

    /**
     * 실무 권장 패턴: graceful shutdown
     * Oracle 공식 문서 권장 방식
     */
    private static void demonstrateGracefulShutdown() {
        System.out.println("[실무 권장 패턴 - Graceful Shutdown]");
        System.out.println("  1. shutdown() 호출");
        System.out.println("  2. awaitTermination()으로 일정 시간 대기");
        System.out.println("  3. 시간 초과 시 shutdownNow()로 강제 종료");
        System.out.println("  4. 다시 awaitTermination()으로 확인\n");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        shutdownGracefully(executor, 1, TimeUnit.SECONDS);
        System.out.println("-> 종료 완료\n");
    }

    /**
     * 재사용 가능한 graceful shutdown 유틸리티
     */
    public static void shutdownGracefully(ExecutorService executor, long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(timeout, unit)) {
                    System.err.println("ExecutorService 종료 실패");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
