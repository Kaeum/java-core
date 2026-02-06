package com.example.concurrency.atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * CAS(Compare-And-Swap) 동작 원리 데모
 *
 * CAS의 의사 코드:
 * ```
 * boolean CAS(memory, expected, newValue) {
 *     if (memory.value == expected) {
 *         memory.value = newValue;
 *         return true;
 *     }
 *     return false;
 * }
 * ```
 *
 * CAS의 장단점:
 *
 * 장점:
 * - 락 없이 원자적 연산 (Lock-Free)
 * - 컨텍스트 스위칭 오버헤드 없음
 * - 경합이 적을 때 synchronized보다 빠름
 *
 * 단점:
 * - 경합이 심하면 spin으로 CPU 낭비
 * - ABA 문제 발생 가능
 * - 단일 변수만 원자적으로 처리 가능
 *
 * 생각해볼 점: "ABA 문제란?"
 * - 값이 A -> B -> A로 변경되었을 때, CAS는 변경을 감지하지 못함
 * - 해결: AtomicStampedReference (버전/스탬프 사용)
 */
public class CasDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CAS 동작 원리 ===\n");

        demonstrateCasLoop();
        demonstrateCasRetry();
        demonstrateAbaProblem();
        demonstrateAtomicStampedReference();
    }

    /**
     * CAS 루프 동작 방식 시각화
     */
    private static void demonstrateCasLoop() {
        System.out.println("[CAS 루프 동작 방식]");

        AtomicInteger value = new AtomicInteger(0);

        // CAS 기반 increment 직접 구현
        System.out.println("  CAS 기반 increment:");
        for (int i = 0; i < 3; i++) {
            int oldValue;
            int newValue;
            int attempts = 0;

            do {
                oldValue = value.get();
                newValue = oldValue + 1;
                attempts++;
                // 실제로는 한 번에 성공하지만, 원리를 보여주기 위해 출력
            } while (!value.compareAndSet(oldValue, newValue));

            System.out.println("    " + oldValue + " -> " + newValue + " (시도 횟수: " + attempts + ")");
        }

        System.out.println();
    }

    /**
     * 경합 상황에서 CAS 재시도 관찰
     */
    private static void demonstrateCasRetry() throws Exception {
        System.out.println("[CAS 경합 - 재시도 관찰]");

        AtomicInteger value = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    int attempts = 0;
                    int oldValue;
                    int newValue;

                    do {
                        oldValue = value.get();
                        newValue = oldValue + 1;
                        attempts++;
                    } while (!value.compareAndSet(oldValue, newValue));

                    totalAttempts.addAndGet(attempts);
                    successCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int total = threadCount * incrementsPerThread;
        int attempts = totalAttempts.get();
        System.out.println("  총 성공 횟수: " + successCount.get());
        System.out.println("  총 시도 횟수: " + attempts);
        System.out.println("  재시도 비율: " + String.format("%.2f", (double) (attempts - total) / total * 100) + "%");
        System.out.println("  -> 경합이 심할수록 재시도 증가");

        System.out.println();
    }

    /**
     * ABA 문제 데모
     * - 값이 A -> B -> A로 변경되어도 CAS는 감지 못함
     */
    private static void demonstrateAbaProblem() throws Exception {
        System.out.println("[ABA 문제]");

        AtomicInteger value = new AtomicInteger(100);

        // 스레드1: 값을 읽고 잠시 대기 후 CAS 시도
        Thread thread1 = new Thread(() -> {
            int expected = value.get();  // 100 읽음
            System.out.println("  스레드1: 값 읽음 = " + expected);

            sleep(500);  // 대기 중에 다른 스레드가 값 변경

            boolean success = value.compareAndSet(expected, 200);
            System.out.println("  스레드1: CAS(100, 200) = " + success);
            System.out.println("  -> ABA를 감지하지 못하고 성공!");
        });

        // 스레드2: A -> B -> A 변경
        Thread thread2 = new Thread(() -> {
            sleep(100);
            int oldValue = value.get();
            value.set(50);   // 100 -> 50 (A -> B)
            System.out.println("  스레드2: 100 -> 50 변경");

            sleep(100);
            value.set(100);  // 50 -> 100 (B -> A)
            System.out.println("  스레드2: 50 -> 100 복원 (ABA!)");
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        System.out.println("  최종값: " + value.get());
        System.out.println();
    }

    /**
     * AtomicStampedReference로 ABA 문제 해결
     * - 값과 함께 스탬프(버전)을 저장
     * - CAS 시 값과 스탬프 모두 일치해야 성공
     */
    private static void demonstrateAtomicStampedReference() throws Exception {
        System.out.println("[AtomicStampedReference - ABA 해결]");

        // 초기값 100, 스탬프 0
        AtomicStampedReference<Integer> stampedRef = new AtomicStampedReference<>(100, 0);

        // 스레드1: 값과 스탬프를 읽고 CAS 시도
        Thread thread1 = new Thread(() -> {
            int[] stampHolder = new int[1];
            Integer expected = stampedRef.get(stampHolder);
            int expectedStamp = stampHolder[0];
            System.out.println("  스레드1: 값=" + expected + ", 스탬프=" + expectedStamp);

            sleep(500);

            boolean success = stampedRef.compareAndSet(
                expected, 200,
                expectedStamp, expectedStamp + 1
            );
            System.out.println("  스레드1: CAS 결과 = " + success);
            if (!success) {
                System.out.println("  -> 스탬프 불일치로 ABA 감지!");
            }
        });

        // 스레드2: A -> B -> A 변경 (스탬프 증가)
        Thread thread2 = new Thread(() -> {
            sleep(100);
            int[] stampHolder = new int[1];
            Integer current = stampedRef.get(stampHolder);
            int stamp = stampHolder[0];

            stampedRef.compareAndSet(current, 50, stamp, stamp + 1);
            System.out.println("  스레드2: 100 -> 50, 스탬프: " + stamp + " -> " + (stamp + 1));

            sleep(100);
            current = stampedRef.get(stampHolder);
            stamp = stampHolder[0];

            stampedRef.compareAndSet(current, 100, stamp, stamp + 1);
            System.out.println("  스레드2: 50 -> 100, 스탬프: " + stamp + " -> " + (stamp + 1));
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        int[] finalStamp = new int[1];
        Integer finalValue = stampedRef.get(finalStamp);
        System.out.println("  최종: 값=" + finalValue + ", 스탬프=" + finalStamp[0]);
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
