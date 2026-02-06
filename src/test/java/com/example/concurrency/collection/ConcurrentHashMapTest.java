package com.example.concurrency.collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConcurrentHashMap 테스트
 */
class ConcurrentHashMapTest {

    @Test
    @DisplayName("ConcurrentHashMap은 동시 쓰기에서 데이터 손실이 없다")
    void concurrentWritesAreThreadSafe() throws Exception {
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        int threadCount = 10;
        int entriesPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < entriesPerThread; i++) {
                    int key = threadId * entriesPerThread + i;
                    map.put(key, key);
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * entriesPerThread, map.size());
    }

    @Test
    @DisplayName("putIfAbsent는 키가 없을 때만 삽입한다")
    void putIfAbsentOnlyInsertsWhenAbsent() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // 첫 삽입: 성공
        Integer prev1 = map.putIfAbsent("key", 100);
        assertNull(prev1);
        assertEquals(100, map.get("key"));

        // 두 번째 삽입: 무시
        Integer prev2 = map.putIfAbsent("key", 200);
        assertEquals(100, prev2);  // 기존 값 반환
        assertEquals(100, map.get("key"));  // 값 변경 안됨
    }

    @Test
    @DisplayName("computeIfAbsent는 없을 때만 계산하여 삽입한다")
    void computeIfAbsentOnlyComputesWhenAbsent() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        int[] computeCount = {0};

        // 첫 호출: 계산 실행
        Integer v1 = map.computeIfAbsent("key", k -> {
            computeCount[0]++;
            return 100;
        });
        assertEquals(100, v1);
        assertEquals(1, computeCount[0]);

        // 두 번째 호출: 계산 안 함
        Integer v2 = map.computeIfAbsent("key", k -> {
            computeCount[0]++;
            return 200;
        });
        assertEquals(100, v2);  // 기존 값
        assertEquals(1, computeCount[0]);  // 계산 안 함
    }

    @Test
    @DisplayName("compute는 항상 계산하여 갱신한다")
    void computeAlwaysComputes() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("counter", 0);

        // increment
        Integer v1 = map.compute("counter", (k, v) -> v + 1);
        assertEquals(1, v1);

        Integer v2 = map.compute("counter", (k, v) -> v + 1);
        assertEquals(2, v2);

        // null 반환 시 삭제
        Integer v3 = map.compute("counter", (k, v) -> null);
        assertNull(v3);
        assertFalse(map.containsKey("counter"));
    }

    @Test
    @DisplayName("merge는 기존 값과 새 값을 결합한다")
    void mergeCombinesOldAndNewValues() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // 키 없음: 새 값 삽입
        Integer v1 = map.merge("key", 10, Integer::sum);
        assertEquals(10, v1);

        // 키 있음: 기존 값 + 새 값
        Integer v2 = map.merge("key", 5, Integer::sum);
        assertEquals(15, v2);

        // 단어 카운터로 활용
        ConcurrentHashMap<String, Integer> wordCount = new ConcurrentHashMap<>();
        String[] words = {"a", "b", "a", "c", "a"};
        for (String word : words) {
            wordCount.merge(word, 1, Integer::sum);
        }
        assertEquals(3, wordCount.get("a"));
        assertEquals(1, wordCount.get("b"));
        assertEquals(1, wordCount.get("c"));
    }

    @Test
    @DisplayName("remove(key, value)는 값이 일치할 때만 삭제한다")
    void conditionalRemoveOnlyRemovesWhenValueMatches() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("key", 100);

        // 값 불일치: 삭제 안됨
        assertFalse(map.remove("key", 999));
        assertTrue(map.containsKey("key"));

        // 값 일치: 삭제
        assertTrue(map.remove("key", 100));
        assertFalse(map.containsKey("key"));
    }

    @Test
    @DisplayName("replace(key, oldValue, newValue)는 값이 일치할 때만 교체한다")
    void conditionalReplaceOnlyReplacesWhenValueMatches() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("key", 100);

        // 값 불일치: 교체 안됨
        assertFalse(map.replace("key", 999, 200));
        assertEquals(100, map.get("key"));

        // 값 일치: 교체
        assertTrue(map.replace("key", 100, 200));
        assertEquals(200, map.get("key"));
    }

    @Test
    @DisplayName("동시 compute로 카운터를 증가시킬 수 있다")
    void concurrentComputeForCounter() throws Exception {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("counter", 0);

        int threadCount = 10;
        int incrementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    map.compute("counter", (k, v) -> v + 1);
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * incrementsPerThread, map.get("counter"));
    }
}
