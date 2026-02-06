package com.example.concurrency.collection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Producer-Consumer 패턴
 *
 * 패턴 개요:
 * - Producer: 데이터 생성 -> 큐에 삽입
 * - Consumer: 큐에서 데이터 꺼내기 -> 처리
 * - BlockingQueue가 자연스럽게 동기화 처리
 *
 * 장점:
 * - Producer/Consumer 속도 차이 완충 (버퍼 역할)
 * - 락 관리 불필요 (BlockingQueue가 처리)
 * - 확장성: Producer/Consumer 수 독립적 조절
 *
 * 실전 사용 사례:
 * - 로그 비동기 처리
 * - 이벤트 큐
 * - 작업 분배 (Thread Pool 내부)
 * - 메시지 브로커 연동 버퍼
 *
 * JD 연결: "대용량 트래픽 처리" - 비동기 처리로 처리량 향상
 */
public class ProducerConsumerDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Producer-Consumer 패턴 ===\n");

        demonstrateBasicPattern();
        demonstrateMultipleConsumers();
        demonstrateGracefulShutdown();
    }

    /**
     * 기본 Producer-Consumer 패턴
     */
    private static void demonstrateBasicPattern() throws Exception {
        System.out.println("[기본 패턴]");

        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);
        AtomicBoolean running = new AtomicBoolean(true);

        // Producer
        Thread producer = new Thread(() -> {
            int item = 0;
            try {
                while (running.get() && item < 10) {
                    queue.put(item);
                    System.out.println("  [P] 생산: " + item);
                    item++;
                    Thread.sleep(100);  // 생산 속도 조절
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("  [P] 생산 종료");
        });

        // Consumer
        Thread consumer = new Thread(() -> {
            try {
                while (running.get() || !queue.isEmpty()) {
                    Integer item = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        System.out.println("  [C] 소비: " + item);
                        Thread.sleep(200);  // 소비가 생산보다 느림
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("  [C] 소비 종료");
        });

        producer.start();
        consumer.start();

        Thread.sleep(3000);
        running.set(false);

        producer.join();
        consumer.join();

        System.out.println();
    }

    /**
     * 다중 Consumer로 처리량 향상
     */
    private static void demonstrateMultipleConsumers() throws Exception {
        System.out.println("[다중 Consumer]");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(20);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger producedCount = new AtomicInteger(0);
        AtomicInteger consumedCount = new AtomicInteger(0);

        // 빠른 Producer
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    String task = "Task-" + i;
                    queue.put(task);
                    producedCount.incrementAndGet();
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.set(false);
            System.out.println("  [P] 생산 완료: " + producedCount.get() + "개");
        });

        // 3개의 느린 Consumer
        ExecutorService consumers = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            final int consumerId = i;
            consumers.submit(() -> {
                try {
                    while (running.get() || !queue.isEmpty()) {
                        String task = queue.poll(200, TimeUnit.MILLISECONDS);
                        if (task != null) {
                            System.out.println("  [C" + consumerId + "] 처리: " + task);
                            consumedCount.incrementAndGet();
                            Thread.sleep(300);  // 느린 처리
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        producer.start();
        producer.join();

        consumers.shutdown();
        consumers.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("  소비 완료: " + consumedCount.get() + "개");
        System.out.println("  -> Consumer 수를 늘려 처리량 향상\n");
    }

    /**
     * Poison Pill을 사용한 우아한 종료
     * https://www.confluent.io/blog/spring-kafka-can-your-kafka-consumers-handle-a-poison-pill/
     */
    private static void demonstrateGracefulShutdown() throws Exception {
        System.out.println("[Poison Pill - 우아한 종료]");

        // Poison Pill: 종료 신호로 사용하는 특별한 값
        final String POISON_PILL = "SHUTDOWN";

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
        int consumerCount = 2;

        // Producer: 작업 생성 후 Poison Pill 전송
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    String task = "Task-" + i;
                    queue.put(task);
                    System.out.println("  [P] 생산: " + task);
                    Thread.sleep(50);
                }

                // Consumer 수만큼 Poison Pill 전송
                for (int i = 0; i < consumerCount; i++) {
                    queue.put(POISON_PILL);
                    System.out.println("  [P] Poison Pill 전송 #" + (i + 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumers: Poison Pill 받으면 종료
        ExecutorService consumers = Executors.newFixedThreadPool(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            consumers.submit(() -> {
                try {
                    while (true) {
                        String item = queue.take();
                        if (POISON_PILL.equals(item)) {
                            System.out.println("  [C" + consumerId + "] Poison Pill 수신, 종료");
                            break;
                        }
                        System.out.println("  [C" + consumerId + "] 처리: " + item);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        producer.start();
        producer.join();

        consumers.shutdown();
        consumers.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  -> Poison Pill로 모든 Consumer 정상 종료");
        System.out.println();
    }
}
