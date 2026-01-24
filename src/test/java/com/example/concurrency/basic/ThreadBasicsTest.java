package com.example.concurrency.basic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread 기초 테스트
 */
class ThreadBasicsTest {

    @Test
    @DisplayName("Thread 생성 직후 상태는 NEW")
    void threadStateAfterCreation() {
        Thread thread = new Thread(() -> {});
        assertEquals(Thread.State.NEW, thread.getState());
    }

    @Test
    @DisplayName("start() 호출 후 상태는 RUNNABLE 또는 TERMINATED")
    void threadStateAfterStart() throws InterruptedException {
        Thread thread = new Thread(() -> {});
        thread.start();
        // 매우 짧은 작업이므로 RUNNABLE 또는 TERMINATED
        Thread.State state = thread.getState();
        assertTrue(state == Thread.State.RUNNABLE || state == Thread.State.TERMINATED);
        thread.join();
    }

    @Test
    @DisplayName("join() 후 상태는 TERMINATED")
    void threadStateAfterJoin() throws InterruptedException {
        Thread thread = new Thread(() -> {});
        thread.start();
        thread.join();
        assertEquals(Thread.State.TERMINATED, thread.getState());
    }

    @Test
    @DisplayName("sleep() 중 상태는 TIMED_WAITING")
    void threadStateDuringSleep() throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();
        Thread.sleep(100); // sleep 상태가 되기를 기다림
        assertEquals(Thread.State.TIMED_WAITING, thread.getState());
        thread.interrupt(); // 테스트 종료를 위해 인터럽트
        thread.join();
    }

    @Test
    @DisplayName("wait() 중 상태는 WAITING")
    void threadStateDuringWait() throws InterruptedException {
        Object lock = new Object();
        Thread thread = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        thread.start();
        Thread.sleep(100); // wait 상태가 되기를 기다림
        assertEquals(Thread.State.WAITING, thread.getState());

        // notify로 깨우기
        synchronized (lock) {
            lock.notify();
        }
        thread.join();
    }

    @Test
    @DisplayName("동기화 없이 공유 변수 증가 시 Race Condition 발생")
    void raceConditionOccurs() throws InterruptedException {
        final int[] count = {0};
        int numThreads = 10;
        int iterations = 10000;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    count[0]++; // NOT thread-safe
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expected = numThreads * iterations;
        // Race Condition으로 인해 실제 값은 기대값보다 작을 가능성이 높음
        System.out.println("Expected: " + expected + ", Actual: " + count[0]);
        // 항상 실패하지는 않지만, 대부분의 경우 손실 발생
    }

    @Test
    @DisplayName("synchronized로 동기화하면 Race Condition 방지")
    void synchronizedPreventsRaceCondition() throws InterruptedException {
        final int[] count = {0};
        final Object lock = new Object();
        int numThreads = 10;
        int iterations = 10000;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    synchronized (lock) {
                        count[0]++; // thread-safe with synchronized
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expected = numThreads * iterations;
        assertEquals(expected, count[0]);
    }
}
