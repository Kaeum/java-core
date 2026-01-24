package com.example.concurrency.basic;

/**
 * Java Thread 기초
 *
 * 핵심 개념:
 * 1. Thread 생성 방법: Thread 상속 vs Runnable 구현
 * 2. Thread 생명주기: NEW -> RUNNABLE -> (BLOCKED/WAITING/TIMED_WAITING) -> TERMINATED
 * 3. 주요 메서드: start(), run(), sleep(), join(), interrupt()
 */
public class ThreadBasics {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Thread 생성 방법 ===\n");

        // 방법 1: Thread 클래스 상속 (권장하지 않음 - 단일 상속 제약)
        Thread thread1 = new MyThread();
        thread1.setName("MyThread");

        // 방법 2: Runnable 구현 (권장 - 유연성)
        Thread thread2 = new Thread(new MyRunnable());
        thread2.setName("MyRunnable");

        // 방법 3: Lambda (Java 8+, 가장 간결)
        Thread thread3 = new Thread(() -> {
            System.out.println("[" + Thread.currentThread().getName() + "] Lambda 실행");
        });
        thread3.setName("LambdaThread");

        // start() vs run() 차이
        // - start(): 새로운 스레드에서 run() 실행 (비동기)
        // - run(): 현재 스레드에서 직접 실행 (동기) - 잘못된 사용
        thread1.start();
        thread2.start();
        thread3.start();

        // join(): 해당 스레드가 종료될 때까지 대기
        thread1.join();
        thread2.join();
        thread3.join();

        System.out.println("\n=== Thread 상태 확인 ===\n");
        demonstrateThreadStates();
    }

    /**
     * Thread 생명주기 (상태)
     *
     * NEW         : 생성되었지만 start() 호출 전
     * RUNNABLE    : 실행 중 또는 실행 대기 (OS 스케줄러 대기)
     * BLOCKED     : synchronized 블록 진입 대기 (모니터 락 대기)
     * WAITING     : 다른 스레드의 특정 작업 완료 대기 (wait(), join())
     * TIMED_WAITING: 시간 제한이 있는 대기 (sleep(), wait(timeout))
     * TERMINATED  : 실행 완료
     */
    private static void demonstrateThreadStates() throws InterruptedException {
        Object lock = new Object();

        Thread thread = new Thread(() -> {
            try {
                // TIMED_WAITING 상태로 전환
                Thread.sleep(100);

                synchronized (lock) {
                    // WAITING 상태로 전환 (notify 대기)
                    lock.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // NEW 상태
        System.out.println("생성 직후: " + thread.getState()); // NEW

        thread.start();
        // RUNNABLE 상태
        System.out.println("start() 후: " + thread.getState()); // RUNNABLE

        Thread.sleep(50);
        // TIMED_WAITING 상태 (sleep 중)
        System.out.println("sleep 중: " + thread.getState()); // TIMED_WAITING

        Thread.sleep(100);
        // WAITING 상태 (wait 중)
        System.out.println("wait 중: " + thread.getState()); // WAITING

        // notify로 깨우기
        synchronized (lock) {
            lock.notify();
        }

        thread.join();
        // TERMINATED 상태
        System.out.println("종료 후: " + thread.getState()); // TERMINATED
    }

    // Thread 상속 방식
    static class MyThread extends Thread {
        @Override
        public void run() {
            System.out.println("[" + getName() + "] Thread 상속 방식 실행");
        }
    }

    // Runnable 구현 방식
    static class MyRunnable implements Runnable {
        @Override
        public void run() {
            System.out.println("[" + Thread.currentThread().getName() + "] Runnable 구현 방식 실행");
        }
    }
}
