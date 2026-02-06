package com.example.concurrency.collection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * BlockingQueue 기초
 *
 * BlockingQueue란?
 * - 큐가 비어있으면 take() 시 블로킹
 * - 큐가 가득 차면 put() 시 블로킹
 * - 무의미한 Busy Waiting 상황을 방지
 * - Producer-Consumer 패턴의 핵심 자료구조
 *
 * 주요 메서드:
 *
 * | 메서드   | 블로킹   | 타임아웃  | 즉시 반환  | 예외
 * |--------|--------|---------|----------|------
 * | put    | O      | -       | -        | 인터럽트만
 * | take   | O      | -       | -        | 인터럽트만
 * | offer  | -      | O (옵션) | O        | false 반환
 * | poll   | -      | O (옵션) | O        | null 반환
 * | add    | -      | -       | O        | IllegalStateException
 * | remove | -      | -       | O        | NoSuchElementException
 *
 * 주요 구현체:
 * - ArrayBlockingQueue: 고정 크기 배열 기반
 * - LinkedBlockingQueue: 연결 리스트 기반 (기본 무제한)
 * - PriorityBlockingQueue: 우선순위 힙 기반
 * - SynchronousQueue: 버퍼 없음, 직접 핸드오프
 *
 * 면접 질문: "put/take vs offer/poll 차이는?"
 * - put/take: 블로킹 (공간 없으면/비어있으면 대기)
 * - offer/poll: 논블로킹 (즉시 false/null 반환)
 * - offer(timeout)/poll(timeout): 지정 시간만 대기
 */
public class BlockingQueueDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== BlockingQueue 기초 ===\n");

        demonstrateBasicOperations();
        demonstrateBlockingBehavior();
        demonstrateTimeoutOperations();
        demonstrateQueueTypes();
    }

    /**
     * 기본 연산
     */
    private static void demonstrateBasicOperations() throws Exception {
        System.out.println("[기본 연산]");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

        // 삽입
        queue.put("A");        // 블로킹 삽입
        queue.offer("B");      // 논블로킹 삽입 (실패 시 false)
        queue.add("C");        // 논블로킹 삽입 (실패 시 예외)

        System.out.println("  큐 상태: " + queue);

        // offer 실패 (가득 참)
        boolean success = queue.offer("D");
        System.out.println("  offer(D) 결과: " + success + " (큐 가득 참)");

        // 조회
        System.out.println("  peek(): " + queue.peek() + " (제거 안 함)");

        // 제거
        String item1 = queue.take();   // 블로킹 제거
        String item2 = queue.poll();   // 논블로킹 제거 (비어있으면 null)
        System.out.println("  take(): " + item1);
        System.out.println("  poll(): " + item2);
        System.out.println("  큐 상태: " + queue);

        System.out.println();
    }

    /**
     * 블로킹 동작 데모
     */
    private static void demonstrateBlockingBehavior() throws Exception {
        System.out.println("[블로킹 동작]");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        // Consumer: 큐가 비어있으면 대기
        Thread consumer = new Thread(() -> {
            try {
                System.out.println("  Consumer: take() 호출 (큐 비어있음, 대기...)");
                String item = queue.take();  // 블로킹!
                System.out.println("  Consumer: 받음 -> " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        Thread.sleep(500);  // consumer가 먼저 대기하도록

        // Producer: 아이템 삽입
        System.out.println("  Producer: put(Hello)");
        queue.put("Hello");

        consumer.join();

        // Producer 블로킹
        queue.put("A");
        queue.put("B");
        System.out.println("  큐 상태: " + queue + " (가득 참)");

        Thread producer = new Thread(() -> {
            try {
                System.out.println("  Producer: put(C) 호출 (큐 가득 참, 대기...)");
                queue.put("C");  // 블로킹!
                System.out.println("  Producer: 삽입 성공");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        Thread.sleep(500);

        System.out.println("  Consumer: take() 호출");
        queue.take();  // 공간 확보

        producer.join();

        System.out.println();
    }

    /**
     * 타임아웃 연산
     */
    private static void demonstrateTimeoutOperations() throws Exception {
        System.out.println("[타임아웃 연산]");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
        queue.put("A");
        queue.put("B");

        // offer with timeout: 1초만 대기
        System.out.println("  offer(C, 1초) 시도 (큐 가득 참)...");
        boolean success = queue.offer("C", 1, TimeUnit.SECONDS);
        System.out.println("  결과: " + success + " (타임아웃)");

        // poll with timeout: 빈 큐에서 1초 대기
        queue.clear();
        System.out.println("  poll(1초) 시도 (큐 비어있음)...");
        String item = queue.poll(1, TimeUnit.SECONDS);
        System.out.println("  결과: " + item + " (타임아웃)");

        System.out.println();
    }

    /**
     * 주요 구현체 비교
     */
    private static void demonstrateQueueTypes() throws Exception {
        System.out.println("[BlockingQueue 구현체]");

        // 1. ArrayBlockingQueue: 고정 크기, 공정성 옵션
        System.out.println("\n  1. ArrayBlockingQueue (고정 크기)");
        ArrayBlockingQueue<String> arrayQueue = new ArrayBlockingQueue<>(10, true);  // fair=true
        System.out.println("     - 용량: 고정 (생성 시 지정)");
        System.out.println("     - 내부 구조: 배열");
        System.out.println("     - 공정성: 선택 가능 (fair 파라미터 - true면 FIFO)");
        System.out.println("     - 사용 사례: 크기 제한이 필요한 버퍼");

        // 2. LinkedBlockingQueue: 가변 크기 (기본 무제한)
        System.out.println("\n  2. LinkedBlockingQueue (가변 크기)");
        LinkedBlockingQueue<String> linkedQueue = new LinkedBlockingQueue<>();  // 무제한
        LinkedBlockingQueue<String> boundedLinked = new LinkedBlockingQueue<>(100);  // 제한
        System.out.println("     - 용량: 기본 무제한 (Integer.MAX_VALUE)");
        System.out.println("     - 내부 구조: 연결 리스트");
        System.out.println("     - 특징: put/take 락 분리 ('ArrayBlockingQueue'보다 처리량 높음)");
        System.out.println("     - 사용 사례: 고처리량 Producer-Consumer");

        // 3. PriorityBlockingQueue: 우선순위 기반
        System.out.println("\n  3. PriorityBlockingQueue (우선순위)");
        PriorityBlockingQueue<Integer> priorityQueue = new PriorityBlockingQueue<>();
        priorityQueue.put(30);
        priorityQueue.put(10);
        priorityQueue.put(20);
        System.out.println("     - 삽입 순서: 30, 10, 20");
        System.out.println("     - 꺼낸 순서: " + priorityQueue.take() + ", " +
                priorityQueue.take() + ", " + priorityQueue.take());
        System.out.println("     - 특징: 무제한 용량, 자연 순서 또는 Comparator");
        System.out.println("     - 사용 사례: 작업 우선순위 스케줄링");

        // 4. SynchronousQueue: 버퍼 없음 (Transfer 역할)
        // https://www.baeldung.com/java-synchronous-queue
        System.out.println("\n  4. SynchronousQueue (버퍼 없음)");
        SynchronousQueue<String> syncQueue = new SynchronousQueue<>();
        System.out.println("     - 용량: 0 (직접 핸드오프)");
        System.out.println("     - 특징: put은 take가 호출될 때까지 블로킹");
        System.out.println("     - 사용 사례: 직접 전달이 필요한 경우. 실무에서 쓸 일이 있을까?");

        // SynchronousQueue 동작
        Thread producer = new Thread(() -> {
            try {
                System.out.println("     Producer: put(X) 호출...");
                syncQueue.put("X");
                System.out.println("     Producer: 전달 완료!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("     Consumer: take() 호출...");
                String item = syncQueue.take();
                System.out.println("     Consumer: 받음 -> " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println();
    }
}
