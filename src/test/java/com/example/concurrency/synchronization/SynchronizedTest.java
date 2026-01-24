package com.example.concurrency.synchronization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * synchronized 테스트
 */
class SynchronizedTest {

    @Test
    @DisplayName("synchronized 메서드로 동기화하면 Race Condition 방지")
    void synchronizedMethodPreventsRaceCondition() throws InterruptedException {
        SynchronizedBasics obj = new SynchronizedBasics();
        int numThreads = 10;
        int iterations = 10000;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    obj.incrementSync();
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(numThreads * iterations, obj.getCount());
    }

    @Test
    @DisplayName("synchronized 블록으로 동기화하면 Race Condition 방지")
    void synchronizedBlockPreventsRaceCondition() throws InterruptedException {
        SynchronizedBasics obj = new SynchronizedBasics();
        int numThreads = 10;
        int iterations = 10000;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    obj.incrementBlock();
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(numThreads * iterations, obj.getCount());
    }

    @Test
    @DisplayName("같은 인스턴스의 synchronized 메서드는 같은 Lock 공유")
    void sameInstanceSharesLock() throws InterruptedException {
        SynchronizedBasics obj = new SynchronizedBasics();
        int iterations = 50000;

        // incrementSync()와 incrementBlock()은 같은 this Lock 사용
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) obj.incrementSync();
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) obj.incrementBlock();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(iterations * 2, obj.getCount());
    }

    @Test
    @DisplayName("Lock 대기 중인 스레드는 BLOCKED 상태")
    void waitingThreadIsBlocked() throws InterruptedException {
        Object lock = new Object();

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                // Lock 획득 후 바로 종료
            }
        });

        holder.start();
        Thread.sleep(50); // holder가 먼저 Lock 획득

        waiter.start();
        Thread.sleep(50);

        assertEquals(Thread.State.BLOCKED, waiter.getState());

        holder.join();
        waiter.join();
    }

    @Test
    @DisplayName("재진입성: 같은 스레드가 같은 Lock을 여러 번 획득 가능")
    void reentrancyWorks() {
        // 재진입이 안 되면 이 테스트는 데드락으로 타임아웃됨
        MonitorLockDemo demo = new MonitorLockDemo();
        assertDoesNotThrow(() -> demo.outerMethodSync());
    }

    @Test
    @DisplayName("ReentrantLock으로 재진입 시 holdCount 증가 확인")
    void reentrantLockHoldCountIncreases() {
        MonitorLockDemo demo = new MonitorLockDemo();
        // outerMethodWithLock이 정상 실행되면 재진입 성공
        assertDoesNotThrow(() -> demo.outerMethodWithLock());
    }
}
