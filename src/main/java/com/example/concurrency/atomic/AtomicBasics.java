package com.example.concurrency.atomic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic 클래스 기초
 *
 * CAS(Compare-And-Swap) 기반 원자적 연산:
 * - 락 없이 동시성 보장 (Lock-Free)
 * - 내부적으로 CPU의 CAS 명령어 사용 (x86: CMPXCHG)
 *
 * 동작 원리:
 * 1. 현재값 읽기
 * 2. 새 값 계산
 * 3. CAS(기대값, 새값) - 기대값과 현재값이 같으면 새값으로 교체
 * 4. 실패하면 1번부터 재시도 (spin)
 *
 * 주요 클래스:
 * - AtomicInteger, AtomicLong: 정수형 원자적 연산
 * - AtomicBoolean: boolean 원자적 연산
 * - AtomicReference<V>: 참조 원자적 연산
 * - AtomicStampedReference<V>: ABA 문제 해결 (스탬프 포함)
 *
 * 면접 질문: "CAS(Compare-And-Swap)란?"
 * - 락 없이 원자적 연산을 수행하는 기법
 * - 현재값이 기대값과 같을 때만 새 값으로 교체
 * - 실패 시 재시도 (낙관적 동시성 제어)
 * - 경합이 적을 때 synchronized보다 성능이 좋음
 */
public class AtomicBasics {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Atomic 클래스 기초 ===\n");

        demonstrateRaceCondition();
        demonstrateAtomicInteger();
        demonstrateCompareAndSet();
        demonstrateUpdateAndGet();
        demonstrateAtomicReference();
    }

    /**
     * 일반 int의 Race Condition 문제
     */
    private static void demonstrateRaceCondition() throws Exception {
        System.out.println("[Race Condition - 일반 int]");

        class Counter {
            int count = 0;

            void increment() {
                count++;  // read-modify-write: 원자적이지 않음
            }
        }

        Counter counter = new Counter();
        int threadCount = 10;
        int incrementsPerThread = 10000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        int expected = threadCount * incrementsPerThread;
        System.out.println("  기대값: " + expected);
        System.out.println("  실제값: " + counter.count);
        System.out.println("  손실: " + (expected - counter.count) + " (Race Condition)");
        System.out.println();
    }

    /**
     * AtomicInteger로 Race Condition 해결
     */
    private static void demonstrateAtomicInteger() throws Exception {
        System.out.println("[AtomicInteger - Race Condition 해결]");

        AtomicInteger atomicCounter = new AtomicInteger(0);
        int threadCount = 10;
        int incrementsPerThread = 10000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    atomicCounter.incrementAndGet();  // CAS 기반 원자적 증가
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        int expected = threadCount * incrementsPerThread;
        System.out.println("  기대값: " + expected);
        System.out.println("  실제값: " + atomicCounter.get());
        System.out.println("  -> 손실 없음 (CAS 기반 원자적 연산)");
        System.out.println();
    }

    /**
     * compareAndSet: CAS 직접 사용
     * - 현재값이 기대값과 같을 때만 새 값으로 교체
     * - 성공하면 true, 실패하면 false
     */
    private static void demonstrateCompareAndSet() {
        System.out.println("[compareAndSet - CAS 직접 사용]");

        AtomicInteger value = new AtomicInteger(10);

        // CAS 성공: 현재값(10) == 기대값(10)
        boolean success = value.compareAndSet(10, 20);
        System.out.println("  compareAndSet(10, 20): " + success + ", 현재값: " + value.get());

        // CAS 실패: 현재값(20) != 기대값(10)
        boolean fail = value.compareAndSet(10, 30);
        System.out.println("  compareAndSet(10, 30): " + fail + ", 현재값: " + value.get());

        // CAS로 직접 increment 구현
        System.out.println("\n  [CAS로 increment 구현]");
        int oldValue;
        int newValue;
        do {
            oldValue = value.get();
            newValue = oldValue + 1;
            System.out.println("    시도: " + oldValue + " -> " + newValue);
        } while (!value.compareAndSet(oldValue, newValue));
        System.out.println("    최종값: " + value.get());

        System.out.println();
    }

    /**
     * updateAndGet / getAndUpdate: 함수형 업데이트
     * - 내부적으로 CAS 루프 사용
     * - 람다로 업데이트 로직 전달
     */
    private static void demonstrateUpdateAndGet() {
        System.out.println("[updateAndGet / getAndUpdate - 함수형 업데이트]");

        AtomicInteger value = new AtomicInteger(10);

        // updateAndGet: 업데이트 후 새 값 반환
        int result1 = value.updateAndGet(v -> v * 2);
        System.out.println("  updateAndGet(v -> v * 2): " + result1);

        // getAndUpdate: 업데이트 전 이전 값 반환
        int result2 = value.getAndUpdate(v -> v + 5);
        System.out.println("  getAndUpdate(v -> v + 5): 이전값 " + result2 + ", 현재값 " + value.get());

        // accumulateAndGet: 다른 값과 결합
        int result3 = value.accumulateAndGet(3, (left, right) -> left * right);
        System.out.println("  accumulateAndGet(3, (c, x) -> c * x): " + result3);

        System.out.println();
    }

    /**
     * AtomicReference: 참조 타입의 원자적 연산
     */
    private static void demonstrateAtomicReference() {
        System.out.println("[AtomicReference - 참조 원자적 연산]");

        record User(String name, int age) {}

        AtomicReference<User> userRef = new AtomicReference<>(new User("Alice", 25));
        System.out.println("  초기값: " + userRef.get());

        // 참조 교체
        User oldUser = userRef.get();
        User newUser = new User("Bob", 30);
        boolean success = userRef.compareAndSet(oldUser, newUser);
        System.out.println("  compareAndSet 결과: " + success + ", 현재값: " + userRef.get());

        // updateAndGet으로 불변 객체 업데이트
        userRef.updateAndGet(user -> new User(user.name(), user.age() + 1));
        System.out.println("  age 증가 후: " + userRef.get());

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
