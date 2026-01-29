package com.example.concurrency.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * thenApply가 어떤 스레드에서 실행되는지 확인
 *
 * 핵심: thenApply "호출"과 "실행"은 다르다
 * - 호출: main 스레드에서 콜백 등록
 * - 실행: 상황에 따라 다른 스레드
 */
public class ThenApplyThreadDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== thenApply 실행 스레드 확인 ===\n");
        System.out.println("main 스레드: " + Thread.currentThread().getName() + "\n");

        scenario1_DuringExecution();
        scenario2_AfterCompletion();
    }

    /**
     * 시나리오 1: 이전 작업 실행 "중"에 thenApply 호출
     * -> 이전 작업 완료한 스레드가 thenApply 실행
     */
    private static void scenario1_DuringExecution() throws Exception {
        System.out.println("[시나리오 1: 이전 작업 진행 중에 thenApply 등록]");

        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("  supplyAsync 시작: " + thread);
            sleep(500);  // 시간이 걸리는 작업
            System.out.println("  supplyAsync 완료: " + thread);
            return "data";
        });

        // 이 시점: supplyAsync 아직 실행 중 (500ms 걸리니까)
        // thenApply는 "등록"만 됨
        System.out.println("  thenApply 등록 (main에서)");

        cf.thenApply(s -> {
            // 이건 supplyAsync 완료 후에 실행됨
            // 완료한 worker 스레드가 이어서 실행
            System.out.println("  thenApply 실행: " + Thread.currentThread().getName());
            return s.toUpperCase();
        }).get();

        System.out.println();
    }

    /**
     * 시나리오 2: 이전 작업 완료 "후"에 thenApply 호출
     * -> 호출 스레드(main)에서 즉시 실행
     */
    private static void scenario2_AfterCompletion() throws Exception {
        System.out.println("[시나리오 2: 이전 작업 완료 후에 thenApply 등록]");

        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("  supplyAsync 실행: " + thread);
            return "data";  // 빠르게 완료
        });

        // 충분히 대기해서 supplyAsync가 확실히 완료되게 함
        sleep(200);
        System.out.println("  (200ms 대기 후 - supplyAsync 이미 완료)");

        // 이 시점: supplyAsync 이미 완료됨
        // thenApply는 main에서 즉시 실행됨
        cf.thenApply(s -> {
            System.out.println("  thenApply 실행: " + Thread.currentThread().getName());
            return s.toUpperCase();
        }).get();

        System.out.println();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}