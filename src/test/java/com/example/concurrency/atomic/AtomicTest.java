package com.example.concurrency.atomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Atomic 클래스 테스트
 */
class AtomicTest {

    @Test
    @DisplayName("AtomicInteger incrementAndGet은 원자적이다")
    void atomicIntegerIsThreadSafe() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 10;
        int incrementsPerThread = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * incrementsPerThread, counter.get());
    }

    @Test
    @DisplayName("compareAndSet은 기대값이 일치할 때만 성공한다")
    void compareAndSetOnlySucceedsWhenExpectedMatches() {
        AtomicInteger value = new AtomicInteger(10);

        // 기대값 일치 -> 성공
        assertTrue(value.compareAndSet(10, 20));
        assertEquals(20, value.get());

        // 기대값 불일치 -> 실패
        assertFalse(value.compareAndSet(10, 30));
        assertEquals(20, value.get());  // 값 변경 안됨
    }

    @Test
    @DisplayName("updateAndGet은 함수를 원자적으로 적용한다")
    void updateAndGetAppliesFunctionAtomically() {
        AtomicInteger value = new AtomicInteger(10);

        int result = value.updateAndGet(v -> v * 2);

        assertEquals(20, result);
        assertEquals(20, value.get());
    }

    @Test
    @DisplayName("getAndUpdate는 이전 값을 반환한다")
    void getAndUpdateReturnsPreviousValue() {
        AtomicInteger value = new AtomicInteger(10);

        int previous = value.getAndUpdate(v -> v * 2);

        assertEquals(10, previous);  // 이전 값
        assertEquals(20, value.get());  // 새 값
    }

    @Test
    @DisplayName("AtomicReference로 참조를 원자적으로 교체할 수 있다")
    void atomicReferenceSwapsReferencesAtomically() {
        record User(String name) {}

        AtomicReference<User> ref = new AtomicReference<>(new User("Alice"));
        User alice = ref.get();
        User bob = new User("Bob");

        // CAS 성공
        assertTrue(ref.compareAndSet(alice, bob));
        assertEquals("Bob", ref.get().name());

        // CAS 실패 (alice는 더 이상 현재값이 아님)
        assertFalse(ref.compareAndSet(alice, new User("Charlie")));
        assertEquals("Bob", ref.get().name());
    }

    @Test
    @DisplayName("AtomicStampedReference는 스탬프로 ABA 문제를 감지한다")
    void atomicStampedReferenceDetectsAba() {
        AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);

        // 초기 상태 저장
        int[] stampHolder = new int[1];
        Integer initialValue = ref.get(stampHolder);
        int initialStamp = stampHolder[0];

        // A -> B -> A 변경 (스탬프 증가)
        ref.compareAndSet(100, 50, 0, 1);  // 100 -> 50, stamp: 0 -> 1
        ref.compareAndSet(50, 100, 1, 2);  // 50 -> 100, stamp: 1 -> 2

        // 값은 같지만 스탬프가 다르므로 실패
        boolean success = ref.compareAndSet(initialValue, 200, initialStamp, initialStamp + 1);

        assertFalse(success);  // ABA 감지!
        assertEquals(100, ref.getReference());
        assertEquals(2, ref.getStamp());
    }

    @Test
    @DisplayName("LongAdder는 동시 쓰기에서도 정확한 합계를 제공한다")
    void longAdderIsAccurateUnderContention() throws Exception {
        LongAdder adder = new LongAdder();
        int threadCount = 10;
        int incrementsPerThread = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    adder.increment();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * incrementsPerThread, adder.sum());
    }

    @Test
    @DisplayName("LongAdder sumThenReset은 합계를 반환하고 리셋한다")
    void longAdderSumThenResetReturnsAndResets() {
        LongAdder adder = new LongAdder();
        adder.add(10);
        adder.add(20);
        adder.add(30);

        long sum = adder.sumThenReset();

        assertEquals(60, sum);
        assertEquals(0, adder.sum());
    }

    @Test
    @DisplayName("accumulateAndGet은 다른 값과 결합한다")
    void accumulateAndGetCombinesWithValue() {
        AtomicLong value = new AtomicLong(10);

        // (현재값 10) * (전달값 3) = 30
        long result = value.accumulateAndGet(3, (current, x) -> current * x);

        assertEquals(30, result);
    }
}
