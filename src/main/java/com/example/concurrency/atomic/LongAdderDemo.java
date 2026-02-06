package com.example.concurrency.atomic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * LongAdder / LongAccumulator - 고성능 카운터
 *
 * AtomicLong의 문제:
 * - 경합이 심하면 CAS 재시도가 많아짐
 * - 모든 스레드가 같은 메모리 위치에 CAS 경쟁
 *
 * LongAdder의 해결책:
 * - 내부적으로 여러 Cell 배열 사용
 * - 각 스레드가 서로 다른 Cell에 쓰기 (경합 분산)
 * - sum() 호출 시 모든 Cell 합산
 *
 * 내부 구조:
 * ```
 * Dynamic Striping 찾아볼 것 -> java.util.concurrent.atomic.Striped64
 * Cache Line의 False Sharing 방지하기 위해 Cell 앞뒤에 Padding(@Contended)하는 구조
 *
 * LongAdder:
 *   base: 0 (경합 없을 때 사용)
 *   cells: [Cell(10), Cell(20), Cell(30), ...]
 *   sum = base + cells[0] + cells[1] + ...
 * ```
 *
 * 언제 사용?
 * - AtomicLong: 현재 정확한 값이 필요할 때
 * - LongAdder: 쓰기가 잦고, 읽기(sum)가 드문 경우 (통계, 카운터)
 *
 * 생각해볼 점: "LongAdder가 AtomicLong보다 빠른 이유는?"
 * - CAS 경합을 여러 Cell로 분산
 * - 스레드별로 다른 Cell에 쓰기
 * - sum() 호출 시만 합산 (약간의 정확도 희생)
 */
public class LongAdderDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== LongAdder / LongAccumulator ===\n");

        demonstrateLongAdderBasics();
        comparePerformance();
        demonstrateLongAccumulator();
    }

    /**
     * LongAdder 기본 사용법
     */
    private static void demonstrateLongAdderBasics() {
        System.out.println("[LongAdder 기본 사용법]");

        LongAdder adder = new LongAdder();

        adder.increment();      // +1
        adder.increment();      // +1
        adder.add(10);          // +10
        adder.decrement();      // -1

        System.out.println("  sum(): " + adder.sum());           // 11
        System.out.println("  sumThenReset(): " + adder.sumThenReset());  // 11, 리셋
        System.out.println("  sum() after reset: " + adder.sum());         // 0

        System.out.println();
    }

    /**
     * AtomicLong vs LongAdder 성능 비교
     */
    private static void comparePerformance() throws Exception {
        System.out.println("[AtomicLong vs LongAdder 성능 비교]");

        int threadCount = 10;
        int incrementsPerThread = 1_000_000;

        // AtomicLong 테스트
        AtomicLong atomicLong = new AtomicLong(0);
        long atomicTime = benchmark(() -> atomicLong.incrementAndGet(), threadCount, incrementsPerThread);
        System.out.println("  AtomicLong:");
        System.out.println("    결과: " + atomicLong.get());
        System.out.println("    시간: " + atomicTime + "ms");

        // LongAdder 테스트
        LongAdder longAdder = new LongAdder();
        long adderTime = benchmark(() -> longAdder.increment(), threadCount, incrementsPerThread);
        System.out.println("  LongAdder:");
        System.out.println("    결과: " + longAdder.sum());
        System.out.println("    시간: " + adderTime + "ms");

        // 비교
        double speedup = (double) atomicTime / adderTime;
        System.out.println("  -> LongAdder가 " + String.format("%.2f", speedup) + "배 빠름");
        System.out.println("  -> 스레드가 많고 쓰기가 잦을수록 차이가 커짐");

        System.out.println();
    }

    private static long benchmark(Runnable task, int threadCount, int iterationsPerThread) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long start = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    task.run();
                }
                latch.countDown();
            });
        }

        latch.await();
        long end = System.currentTimeMillis();

        executor.shutdown();
        return end - start;
    }

    /**
     * LongAccumulator: 커스텀 연산
     * - LongAdder는 덧셈만 가능
     * - LongAccumulator는 임의의 이항 연산 가능
     */
    private static void demonstrateLongAccumulator() throws Exception {
        System.out.println("[LongAccumulator - 커스텀 연산]");

        // 최댓값 찾기 (초기값: Long.MIN_VALUE)
        LongAccumulator maxFinder = new LongAccumulator(Long::max, Long.MIN_VALUE);

        // 여러 스레드에서 값 추가
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);

        int[] values = {42, 17, 99, 73};
        for (int value : values) {
            executor.submit(() -> {
                maxFinder.accumulate(value);
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("  입력값: 42, 17, 99, 73");
        System.out.println("  최댓값: " + maxFinder.get());

        // 곱셈 누적 (초기값: 1)
        LongAccumulator multiplier = new LongAccumulator((a, b) -> a * b, 1);
        multiplier.accumulate(2);  // 1 * 2 = 2
        multiplier.accumulate(3);  // 2 * 3 = 6
        multiplier.accumulate(4);  // 6 * 4 = 24
        System.out.println("  곱셈 누적 (2 * 3 * 4): " + multiplier.get());

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
