package com.example.concurrency.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ReadWriteLock - 읽기/쓰기 분리 락
 *
 * 문제: 읽기 작업이 많은 경우
 * - 단순 Lock 사용 시 읽기끼리도 blocking
 * - 읽기는 동시에 해도 안전한데 성능 낭비
 *
 * 해결: ReadWriteLock
 * - readLock: 여러 스레드가 동시 획득 가능 (공유 락)
 * - writeLock: 한 스레드만 획득 가능 (배타 락)
 *
 * 규칙:
 * | 현재 보유 | read 요청 | write 요청 |
 * |----------|----------|------------|
 * | 없음     | 허용     | 허용       |
 * | read     | 허용     | 대기       |
 * | write    | 대기     | 대기       |
 *
 * 면접 질문: "읽기 비율이 높은 캐시에 어떤 락을 쓰겠는가?"
 * - ReentrantReadWriteLock 사용
 * - 읽기 작업이 90%+ 이면 효과적
 * - 쓰기 비율 높으면 오히려 오버헤드 (락 관리 비용)
 */
public class ReadWriteLockDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ReadWriteLock ===\n");

        //demonstrateBasicUsage();
        demonstrateConcurrentReads();
        demonstrateWriteBlocking();
    }

    /**
     * 기본 사용법
     */
    private static void demonstrateBasicUsage() {
        System.out.println("[기본 사용법]");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();

        // 읽기 락
        rwLock.readLock().lock();
        try {
            System.out.println("  읽기 작업 수행");
        } finally {
            rwLock.readLock().unlock();
        }

        // 쓰기 락
        rwLock.writeLock().lock();
        try {
            System.out.println("  쓰기 작업 수행");
        } finally {
            rwLock.writeLock().unlock();
        }

        System.out.println();
    }

    /**
     * 읽기는 동시 실행 가능
     */
    private static void demonstrateConcurrentReads() throws Exception {
        System.out.println("[동시 읽기]");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Runnable readTask = () -> {
            rwLock.readLock().lock();
            try {
                String name = Thread.currentThread().getName();
                System.out.println("  " + name + ": 읽기 락 획득");
                sleep(500);  // 읽기 작업
                System.out.println("  " + name + ": 읽기 완료");
            } finally {
                rwLock.readLock().unlock();
            }
        };

        long start = System.currentTimeMillis();

        // 3개 읽기 작업 동시 실행
        executor.submit(readTask);
        executor.submit(readTask);
        executor.submit(readTask);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  총 소요: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("  -> 3개 동시 실행되어 ~500ms (순차면 1500ms)");
        System.out.println();
    }

    /**
     * 쓰기는 읽기/쓰기 모두 블로킹
     */
    private static void demonstrateWriteBlocking() throws Exception {
        System.out.println("[쓰기 시 블로킹]");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();

        // 쓰기 스레드
        Thread writer = new Thread(() -> {
            rwLock.writeLock().lock();
            try {
                System.out.println("  writer: 쓰기 락 획득 (1초 작업)");
                sleep(1000);
                System.out.println("  writer: 쓰기 완료");
            } finally {
                rwLock.writeLock().unlock();
            }
        });

        // 읽기 스레드
        Thread reader = new Thread(() -> {
            sleep(100);  // writer가 먼저 락 획득하도록
            System.out.println("  reader: 읽기 락 대기...");
            long start = System.currentTimeMillis();
            rwLock.readLock().lock();
            try {
                long waited = System.currentTimeMillis() - start;
                System.out.println("  reader: 읽기 락 획득 (" + waited + "ms 대기)");
            } finally {
                rwLock.readLock().unlock();
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        System.out.println("  -> 쓰기 중에는 읽기도 대기해야 함");
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

/**
 * 실용 예제: Thread-safe Cache
 */
class ThreadSafeCache<K, V> {

    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public V get(K key) {
        rwLock.readLock().lock();
        try {
            return cache.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        rwLock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public V getOrCompute(K key, java.util.function.Function<K, V> loader) {
        // 1. 먼저 읽기 락으로 확인
        rwLock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                return value;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // 2. 없으면 쓰기 락으로 업그레이드
        rwLock.writeLock().lock();
        try {
            // Double-check: 다른 스레드가 이미 추가했을 수 있음
            V value = cache.get(key);
            if (value != null) {
                return value;
            }
            value = loader.apply(key);
            cache.put(key, value);
            return value;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}