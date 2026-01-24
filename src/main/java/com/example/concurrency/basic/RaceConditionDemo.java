package com.example.concurrency.basic;

/**
 * Race Condition (경쟁 상태) 데모
 *
 * 핵심 개념:
 * 1. Race Condition: 여러 스레드가 공유 자원에 동시 접근할 때 실행 순서에 따라 결과가 달라지는 현상
 * 2. Critical Section: 공유 자원에 접근하는 코드 영역
 * 3. 원인: count++ 연산은 원자적(atomic)이지 않음
 *    - READ: 메모리에서 값 읽기
 *    - MODIFY: 값 증가
 *    - WRITE: 메모리에 값 쓰기
 *
 * 면접 질문: "count++이 Thread-safe하지 않은 이유는?"
 */
public class RaceConditionDemo {

    private static int unsafeCount = 0;
    private static int safeCount = 0;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Race Condition 데모 ===\n");

        int numThreads = 100;
        int incrementsPerThread = 10000;
        int expected = numThreads * incrementsPerThread;

        // 안전하지 않은 카운터 테스트
        unsafeCount = 0;
        Thread[] unsafeThreads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            unsafeThreads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    unsafeCount++; // NOT thread-safe!
                }
            });
        }

        long start = System.currentTimeMillis();
        for (Thread t : unsafeThreads) {
            t.start();
        }
        for (Thread t : unsafeThreads) {
            t.join();
        }
        long unsafeTime = System.currentTimeMillis() - start;

        System.out.println("[Unsafe Counter]");
        System.out.println("기대값: " + expected);
        System.out.println("실제값: " + unsafeCount);
        System.out.println("손실된 연산: " + (expected - unsafeCount));
        System.out.println("소요시간: " + unsafeTime + "ms\n");

        // synchronized를 사용한 안전한 카운터 테스트
        safeCount = 0;
        Object lock = new Object();
        Thread[] safeThreads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            safeThreads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    synchronized (lock) {
                        safeCount++; // thread-safe with synchronized
                    }
                }
            });
        }

        start = System.currentTimeMillis();
        for (Thread t : safeThreads) {
            t.start();
        }
        for (Thread t : safeThreads) {
            t.join();
        }
        long safeTime = System.currentTimeMillis() - start;

        System.out.println("[Safe Counter with synchronized]");
        System.out.println("기대값: " + expected);
        System.out.println("실제값: " + safeCount);
        System.out.println("손실된 연산: " + (expected - safeCount));
        System.out.println("소요시간: " + safeTime + "ms\n");

        System.out.println("=== 분석 ===");
        System.out.println("synchronized 사용 시 정확하지만 " + (safeTime - unsafeTime) + "ms 더 소요됨");
        System.out.println("-> 동기화 비용(Lock 획득/해제)이 존재함");
        System.out.println("-> 이후 Atomic 클래스로 더 효율적인 방법 학습 예정");
    }
}
