package com.example.concurrency.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executor Framework 기초
 *
 * 핵심:
 * - Thread 직접 생성의 문제: 생성/소멸 비용, 무한 증가, 관리 불가
 * - ThreadPool: 스레드를 미리 만들어두고 재사용, 작업은 큐에 넣어서 순서대로 처리
 * - ThreadPool의 가치는 '속도'가 아니라 '리소스 제어'와 '안정성'
 *
 * 생각해볼 점: "newFixedThreadPool과 newCachedThreadPool의 차이는?"
 * - Fixed: 고정 크기, 초과 작업은 큐에서 대기. 안정적이지만 큐가 무한히 쌓일 수 있음
 * - Cached: 필요할 때 생성, 60초 유휴 시 제거. 유연하지만 스레드가 무한 증가 가능
 *
 * CachedThreadPool은 언제 쓰나?
 * - 요청이 간헐적으로 들어올 때 (폭증하는 상황이 아닐 때)
 * - 작업이 짧을 때 (수십ms 이하?)
 * - 유휴 스레드를 재사용하고, 일정 시간 후 자동 정리됨
 * - Thread 직접 생성 대비 장점: 매번 생성/소멸 대신 기존 스레드 재사용
 *
 * 실무에서는 Executors 팩토리 메서드 주의:
 * - FixedThreadPool: 큐가 무한(LinkedBlockingQueue) -> 큐 폭증 시 OOM
 * - CachedThreadPool: 스레드가 무한(Integer.MAX_VALUE) -> 스레드 폭증 시 OOM
 * - 권장: ThreadPoolExecutor를 직접 생성하여 core/max/queue 크기를 명시적으로 제한
 *
 * 예시:
 * new ThreadPoolExecutor(
 *     10,                              // corePoolSize
 *     50,                              // maxPoolSize (제한)
 *     60L, TimeUnit.SECONDS,           // keepAliveTime
 *     new ArrayBlockingQueue<>(100),   // 큐 크기 제한
 *     new ThreadPoolExecutor.CallerRunsPolicy()  // 거부 정책
 * );
 */
public class ExecutorBasics {

    // 생성된 스레드 수 추적용
    private static final AtomicInteger threadCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Executor Framework 기초 ===\n");
        System.out.println("시나리오: 100개의 I/O 작업 (각 100ms 소요)\n");

        demonstrateThreadProblem();
        demonstrateFixedThreadPool();
        demonstrateCachedThreadPool();
        demonstrateResourceControl();
    }

    /**
     * Thread 직접 생성의 문제점
     * - 작업당 스레드 1개 -> 100개 스레드 생성
     */
    private static void demonstrateThreadProblem() throws InterruptedException {
        System.out.println("[Thread 직접 생성]");
        threadCount.set(0);

        int taskCount = 100;
        Thread[] threads = new Thread[taskCount];

        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            threads[i] = new Thread(() -> {
                threadCount.incrementAndGet();
                simulateIoWork();
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  생성된 스레드 수: " + threadCount.get());
        System.out.println("  소요 시간: " + elapsed + "ms");
        System.out.println("  -> 스레드 100개 생성, 동시에 100개 실행\n");
    }

    /**
     * FixedThreadPool: 고정 크기로 리소스 제어
     * - 10개 스레드로 100개 작업 처리
     * - 나머지 90개는 큐에서 대기
     */
    private static void demonstrateFixedThreadPool() throws InterruptedException {
        System.out.println("[FixedThreadPool(10)]");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int taskCount = 100;

        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            executor.submit(ExecutorBasics::simulateIoWork);
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  스레드 수: 10개 (고정)");
        System.out.println("  소요 시간: " + elapsed + "ms");
        System.out.println("  -> 10개 스레드가 100개 작업을 나눠서 처리 (각 10번씩)");
        System.out.println("  -> 100ms * 10 = 약 1000ms 소요\n");
    }

    /**
     * CachedThreadPool: 필요한 만큼 스레드 생성
     * - 100개 작업 동시 제출 -> 거의 100개 스레드 생성
     */
    private static void demonstrateCachedThreadPool() throws InterruptedException {
        System.out.println("[CachedThreadPool]");

        // CachedThreadPool은 ThreadPoolExecutor로 캐스팅 가능
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        int taskCount = 100;

        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            executor.submit(ExecutorBasics::simulateIoWork);
        }

        Thread.sleep(50); // 스레드 생성 대기
        int maxPoolSize = executor.getPoolSize();

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  생성된 스레드 수: " + maxPoolSize);
        System.out.println("  소요 시간: " + elapsed + "ms");
        System.out.println("  -> 스레드가 필요한 만큼 생성됨 (거의 100개)");
        System.out.println("  -> 직접 생성과 비슷하지만, 60초 후 유휴 스레드 자동 정리\n");
    }

    /**
     * 핵심: ThreadPool의 진짜 가치 = 리소스 제어
     * 대량 요청이 들어와도 스레드 폭증 방지
     */
    private static void demonstrateResourceControl() throws InterruptedException {
        System.out.println("[리소스 제어 비교 - 1000개 요청 시뮬레이션]");

        // CachedThreadPool: 스레드 폭증
        ThreadPoolExecutor cached = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        for (int i = 0; i < 1000; i++) {
            cached.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        Thread.sleep(50);
        int cachedThreads = cached.getPoolSize();
        cached.shutdownNow();

        // FixedThreadPool: 안정적
        ThreadPoolExecutor fixed = (ThreadPoolExecutor) Executors.newFixedThreadPool(20);
        for (int i = 0; i < 1000; i++) {
            fixed.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        Thread.sleep(50);
        int fixedThreads = fixed.getPoolSize();
        int queueSize = fixed.getQueue().size();
        fixed.shutdownNow();

        System.out.println("  CachedThreadPool: 스레드 " + cachedThreads + "개 생성");
        System.out.println("  FixedThreadPool:  스레드 " + fixedThreads + "개, 큐 대기 " + queueSize + "개");
    }

    /**
     * I/O 작업 시뮬레이션 (DB 쿼리, API 호출 등)
     */
    private static void simulateIoWork() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
