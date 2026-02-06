package com.example.concurrency.collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlockingQueue 테스트
 */
class BlockingQueueTest {

    @Test
    @DisplayName("put은 공간이 생길 때까지 블로킹한다")
    @Timeout(5)
    void putBlocksUntilSpaceAvailable() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        queue.put(1);  // 큐 가득 참

        AtomicInteger putCompleted = new AtomicInteger(0);

        Thread producer = new Thread(() -> {
            try {
                queue.put(2);  // 블로킹
                putCompleted.set(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        Thread.sleep(100);

        assertEquals(0, putCompleted.get());  // 아직 대기 중

        queue.take();  // 공간 확보
        producer.join(1000);

        assertEquals(1, putCompleted.get());  // put 완료
    }

    @Test
    @DisplayName("take는 아이템이 들어올 때까지 블로킹한다")
    @Timeout(5)
    void takeBlocksUntilItemAvailable() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        AtomicInteger received = new AtomicInteger(-1);

        Thread consumer = new Thread(() -> {
            try {
                int item = queue.take();  // 블로킹
                received.set(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        Thread.sleep(100);

        assertEquals(-1, received.get());  // 아직 대기 중

        queue.put(42);  // 아이템 삽입
        consumer.join(1000);

        assertEquals(42, received.get());  // 수신 완료
    }

    @Test
    @DisplayName("offer는 공간이 없으면 즉시 false를 반환한다")
    void offerReturnsFalseWhenFull() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        queue.put(1);

        boolean result = queue.offer(2);  // 논블로킹

        assertFalse(result);
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("offer(timeout)는 지정 시간만 대기 후 false를 반환한다")
    @Timeout(5)
    void offerWithTimeoutWaitsAndReturnsFalse() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        queue.put(1);

        long start = System.currentTimeMillis();
        boolean result = queue.offer(2, 500, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result);
        assertTrue(elapsed >= 400);  // 최소 500ms 대기
    }

    @Test
    @DisplayName("poll은 큐가 비어있으면 즉시 null을 반환한다")
    void pollReturnsNullWhenEmpty() {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);

        Integer result = queue.poll();  // 논블로킹

        assertNull(result);
    }

    @Test
    @DisplayName("poll(timeout)는 지정 시간만 대기 후 null을 반환한다")
    @Timeout(5)
    void pollWithTimeoutWaitsAndReturnsNull() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);

        long start = System.currentTimeMillis();
        Integer result = queue.poll(500, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertNull(result);
        assertTrue(elapsed >= 400);
    }

    @Test
    @DisplayName("Producer-Consumer 패턴이 정확하게 동작한다")
    @Timeout(10)
    void producerConsumerWorksCorrectly() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        int itemCount = 100;

        AtomicInteger produced = new AtomicInteger(0);
        AtomicInteger consumed = new AtomicInteger(0);
        AtomicInteger sum = new AtomicInteger(0);

        // Producer
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    queue.put(i);
                    produced.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    int item = queue.take();
                    sum.addAndGet(item);
                    consumed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        assertEquals(itemCount, produced.get());
        assertEquals(itemCount, consumed.get());

        // 0 + 1 + 2 + ... + 99 = 4950
        int expectedSum = (itemCount - 1) * itemCount / 2;
        assertEquals(expectedSum, sum.get());
    }

    @Test
    @DisplayName("다중 Producer-Consumer가 정확하게 동작한다")
    @Timeout(10)
    void multipleProducersConsumersWorkCorrectly() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(50);
        int producerCount = 5;
        int consumerCount = 3;
        int itemsPerProducer = 100;
        int totalItems = producerCount * itemsPerProducer;

        AtomicInteger consumed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(producerCount + consumerCount);
        CountDownLatch producerLatch = new CountDownLatch(producerCount);
        CountDownLatch consumerLatch = new CountDownLatch(consumerCount);

        // Producers
        for (int p = 0; p < producerCount; p++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerProducer; i++) {
                        queue.put(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                producerLatch.countDown();
            });
        }

        // Consumers (Poison pill 패턴 사용)
        for (int c = 0; c < consumerCount; c++) {
            executor.submit(() -> {
                try {
                    while (true) {
                        Integer item = queue.poll(1, TimeUnit.SECONDS);
                        if (item == null) {
                            break;  // 1초간 아이템 없으면 종료
                        }
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                consumerLatch.countDown();
            });
        }

        producerLatch.await();
        consumerLatch.await();
        executor.shutdown();

        assertEquals(totalItems, consumed.get());
    }

    @Test
    @DisplayName("PriorityBlockingQueue는 우선순위대로 꺼낸다")
    void priorityQueueMaintainsOrder() throws Exception {
        PriorityBlockingQueue<Integer> queue = new PriorityBlockingQueue<>();

        queue.put(30);
        queue.put(10);
        queue.put(20);

        assertEquals(10, queue.take());  // 가장 작은 값 먼저
        assertEquals(20, queue.take());
        assertEquals(30, queue.take());
    }

    @Test
    @DisplayName("LinkedBlockingQueue는 put/take 락이 분리되어 있다")
    @Timeout(10)
    void linkedBlockingQueueHasSeparateLocks() throws Exception {
        // LinkedBlockingQueue는 put/take 락이 분리되어 동시 수행 가능
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1000);

        int itemCount = 10000;
        AtomicInteger produced = new AtomicInteger(0);
        AtomicInteger consumed = new AtomicInteger(0);

        Thread producer = new Thread(() -> {
            for (int i = 0; i < itemCount; i++) {
                try {
                    queue.put(i);
                    produced.incrementAndGet();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < itemCount; i++) {
                try {
                    queue.take();
                    consumed.incrementAndGet();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        assertEquals(itemCount, produced.get());
        assertEquals(itemCount, consumed.get());
    }
}
