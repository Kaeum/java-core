package com.example.concurrency.collection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentHashMap 동작 원리
 *
 * HashMap의 문제:
 * - 동시 수정 시 무한 루프, 데이터 손실, ConcurrentModificationException
 * - Collections.synchronizedMap: 전체 락으로 성능 저하
 *
 * ConcurrentHashMap의 해결책:
 *
 * Java 7 (세그먼트 기반):
 * - 내부를 16개 Segment로 분할
 * - 각 Segment마다 독립적인 락
 * - 최대 16개 스레드 동시 쓰기 가능
 *
 * Java 8+ (CAS + synchronized):
 * - Segment 제거, Node 배열 직접 사용
 * - 빈 버킷: CAS로 삽입 (락 없음)
 * - 충돌 버킷: 해당 Node만 synchronized
 * - 읽기는 락 없이 volatile 읽기
 *
 * 내부 구조 (Java 8+):
 * ```
 * table: [Node, null, Node, Node(synchronized), ...]
 *         ↑     ↑     ↑
 *        CAS   CAS   CAS (빈 버킷 삽입)
 *                     └── synchronized (충돌 시)
 * ```
 *
 * 생각해볼 점: "ConcurrentHashMap은 어떻게 동시성을 보장하는가?"
 * - Java 8+: CAS + 세분화된 synchronized (Node 단위)
 * - 빈 버킷은 CAS로 락 없이 삽입
 * - 충돌 시 해당 버킷만 synchronized
 * - 읽기는 volatile로 락 없이 수행
 */
public class ConcurrentHashMapDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ConcurrentHashMap ===\n");

        demonstrateHashMapProblem();
        demonstrateConcurrentHashMap();
        demonstrateAtomicOperations();
        demonstrateComputePatterns();
        demonstrateSizeVsMappingCount();
    }

    /**
     * HashMap의 동시성 문제
     */
    private static void demonstrateHashMapProblem() throws Exception {
        System.out.println("[HashMap의 동시성 문제]");

        Map<Integer, Integer> hashMap = new HashMap<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        int threadCount = 10;
        int operationsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = threadId * operationsPerThread + i;
                        hashMap.put(key, key);  // 동시 수정!
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int expected = threadCount * operationsPerThread;
        System.out.println("  기대 크기: " + expected);
        System.out.println("  실제 크기: " + hashMap.size());
        System.out.println("  손실: " + (expected - hashMap.size()) + " 엔트리");
        System.out.println("  -> HashMap은 동시성 안전하지 않음!");

        System.out.println();
    }

    /**
     * ConcurrentHashMap은 동시 수정에 안전
     */
    private static void demonstrateConcurrentHashMap() throws Exception {
        System.out.println("[ConcurrentHashMap - 동시성 안전]");

        ConcurrentHashMap<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();

        int threadCount = 10;
        int operationsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    int key = threadId * operationsPerThread + i;
                    concurrentMap.put(key, key);
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int expected = threadCount * operationsPerThread;
        System.out.println("  기대 크기: " + expected);
        System.out.println("  실제 크기: " + concurrentMap.size());
        System.out.println("  -> 손실 없음!");

        System.out.println();
    }

    /**
     * 원자적 연산: putIfAbsent, remove, replace
     * - Check-Then-Act 패턴을 원자적으로 수행
     */
    private static void demonstrateAtomicOperations() {
        System.out.println("[원자적 연산]");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // putIfAbsent: 없을 때만 삽입
        Integer prev1 = map.putIfAbsent("key1", 100);
        System.out.println("  putIfAbsent(key1, 100): 이전값=" + prev1 + ", 현재값=" + map.get("key1"));

        Integer prev2 = map.putIfAbsent("key1", 200);  // 이미 존재 -> 무시
        System.out.println("  putIfAbsent(key1, 200): 이전값=" + prev2 + ", 현재값=" + map.get("key1"));

        // remove(key, expectedValue): 값이 일치할 때만 삭제
        boolean removed1 = map.remove("key1", 999);  // 불일치 -> 삭제 안됨
        System.out.println("  remove(key1, 999): " + removed1 + ", 현재값=" + map.get("key1"));

        boolean removed2 = map.remove("key1", 100);  // 일치 -> 삭제
        System.out.println("  remove(key1, 100): " + removed2 + ", 현재값=" + map.get("key1"));

        // replace(key, expectedValue, newValue): 값이 일치할 때만 교체
        map.put("key2", 50);
        boolean replaced1 = map.replace("key2", 999, 60);  // 불일치
        System.out.println("  replace(key2, 999, 60): " + replaced1 + ", 현재값=" + map.get("key2"));

        boolean replaced2 = map.replace("key2", 50, 60);  // 일치
        System.out.println("  replace(key2, 50, 60): " + replaced2 + ", 현재값=" + map.get("key2"));

        System.out.println();
    }

    /**
     * compute 패턴: 읽기-수정-쓰기를 원자적으로
     *
     * 잘못된 패턴 (Check-Then-Act):
     * ```
     * Integer old = map.get(key);
     * if (old == null) {
     *     map.put(key, 1);
     * } else {
     *     map.put(key, old + 1);  // 다른 스레드가 수정했을 수 있음!
     * }
     * ```
     */
    private static void demonstrateComputePatterns() throws Exception {
        System.out.println("[compute 패턴 - 원자적 읽기-수정-쓰기]");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // computeIfAbsent: 없으면 계산하여 삽입
        Integer v1 = map.computeIfAbsent("counter", k -> {
            System.out.println("  computeIfAbsent: 초기값 생성");
            return 0;
        });
        System.out.println("  첫 번째 computeIfAbsent: " + v1);

        Integer v2 = map.computeIfAbsent("counter", k -> {
            System.out.println("  이 메시지는 출력되지 않음");
            return 999;
        });
        System.out.println("  두 번째 computeIfAbsent: " + v2 + " (기존 값 반환)");

        // compute: 있든 없든 계산하여 갱신
        Integer v3 = map.compute("counter", (k, v) -> (v == null) ? 1 : v + 1);
        System.out.println("  compute (increment): " + v3);

        // merge: 기존 값과 새 값을 결합
        Integer v4 = map.merge("counter", 10, Integer::sum);  // 기존값 + 10
        System.out.println("  merge(10, sum): " + v4);

        // 동시성 카운터로 활용
        System.out.println("\n  [동시성 단어 카운터 예시]");
        ConcurrentHashMap<String, Integer> wordCount = new ConcurrentHashMap<>();
        String[] words = {"apple", "banana", "apple", "cherry", "banana", "apple"};

        for (String word : words) {
            wordCount.merge(word, 1, Integer::sum);  // 원자적 증가
        }
        System.out.println("  단어 빈도: " + wordCount);

        System.out.println();
    }

    /**
     * size() vs mappingCount()
     * - size(): int 반환 (Integer.MAX_VALUE 초과 시 부정확)
     * - mappingCount(): long 반환 (더 정확)
     * - 둘 다 추정치 (동시 수정 중에는 정확하지 않을 수 있음)
     */
    private static void demonstrateSizeVsMappingCount() {
        System.out.println("[size() vs mappingCount()]");

        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            map.put(i, i);
        }

        System.out.println("  size(): " + map.size());
        System.out.println("  mappingCount(): " + map.mappingCount());
        System.out.println("  -> 대용량에서는 mappingCount() 권장 (long 타입)");
        System.out.println("  -> 동시 수정 중에는 둘 다 추정치");

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
