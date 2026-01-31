package com.example.concurrency.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReentrantLock 기초
 *
 * synchronized vs ReentrantLock:
 *
 * | 항목 | synchronized | ReentrantLock |
 * |------|-------------|---------------|
 * | 락 획득/해제 | 자동 (블록 진입/탈출) | 수동 (lock/unlock) |
 * | 타임아웃 | 불가 | tryLock(timeout) |
 * | 인터럽트 | 불가 | lockInterruptibly() |
 * | 공정성 | 불공정 (비보장) | 선택 가능 (fair=true) |
 * | Condition | wait/notify | await/signal (다중 조건 가능) |
 *
 * 면접 질문: "synchronized 대신 ReentrantLock을 쓰는 경우는?"
 * - 타임아웃이 필요할 때 (tryLock)
 * - 락 획득 중 인터럽트가 필요할 때 (lockInterruptibly)
 * - 공정한 락이 필요할 때 (fair=true)
 * - 여러 Condition이 필요할 때
 * - 락 획득 여부를 확인해야 할 때 (tryLock)
 *
 * 주의: unlock()은 반드시 finally에서!
 */
public class ReentrantLockBasics {

    private final Lock lock = new ReentrantLock();
    private int count = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== ReentrantLock 기초 ===\n");

        //demonstrateBasicUsage();
        demonstrateTryLock();
        demonstrateTryLockTimeout();
        demonstrateLockInterruptibly();
        demonstrateReentrancy();
    }

    /**
     * 기본 사용법: lock() / unlock()
     */
    private static void demonstrateBasicUsage() {
        System.out.println("[기본 사용법]");

        Lock lock = new ReentrantLock();

        // 올바른 패턴: try-finally
        lock.lock();
        try {
            System.out.println("  임계 영역 진입");
            // 작업 수행
        } finally {
            lock.unlock();  // 반드시 finally에서!
            System.out.println("  락 해제");
        }

        // 잘못된 패턴 (절대 금지)
        // lock.lock();
        // doSomething();  // 예외 발생 시 unlock 안 됨!
        // lock.unlock();

        System.out.println();
    }

    /**
     * tryLock(): 락 획득 시도 (non-blocking)
     * - 획득 성공: true 반환, 락 보유
     * - 획득 실패: false 반환, 블로킹 없이 즉시 반환
     */
    private static void demonstrateTryLock() throws Exception {
        System.out.println("[tryLock - 논블로킹 락 획득]");

        Lock lock = new ReentrantLock();

        // 스레드1: 락 보유
        Thread t1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  스레드1: 락 획득, 2초간 보유");
                sleep(2000);
            } finally {
                lock.unlock();
                System.out.println("  스레드1: 락 해제");
            }
        });

        // 스레드2: tryLock으로 시도
        Thread t2 = new Thread(() -> {
            sleep(100);  // t1이 먼저 락 획득하도록
            System.out.println("  스레드2: tryLock 시도");

            if (lock.tryLock()) {
                try {
                    System.out.println("  스레드2: 락 획득 성공");
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("  스레드2: 락 획득 실패 (블로킹 없이 반환)");
                System.out.println("  -> 다른 작업 수행 가능");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println();
    }

    /**
     * tryLock(timeout): 지정 시간만 대기
     */
    private static void demonstrateTryLockTimeout() throws Exception {
        System.out.println("[tryLock(timeout) - 타임아웃]");

        Lock lock = new ReentrantLock();

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  holder: 락 보유 (3초)");
                sleep(3000);
            } finally {
                lock.unlock();
            }
        });

        Thread waiter = new Thread(() -> {
            sleep(100);
            try {
                System.out.println("  waiter: 1초간 락 획득 시도...");
                boolean acquired = lock.tryLock(1, TimeUnit.SECONDS);
                if (acquired) {
                    try {
                        System.out.println("  waiter: 락 획득 성공");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("  waiter: 1초 타임아웃 - 락 획득 포기");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        holder.start();
        waiter.start();
        holder.join();
        waiter.join();

        System.out.println();
    }

    /**
     * lockInterruptibly(): 락 대기 중 인터럽트 가능
     * - lock()은 인터럽트 무시
     * - lockInterruptibly()는 InterruptedException 발생
     */
    private static void demonstrateLockInterruptibly() throws Exception {
        System.out.println("[lockInterruptibly - 인터럽트 가능]");

        Lock lock = new ReentrantLock();

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  holder: 락 보유 (5초)");
                sleep(5000);
            } finally {
                lock.unlock();
            }
        });

        Thread waiter = new Thread(() -> {
            sleep(100);
            try {
                System.out.println("  waiter: lockInterruptibly로 대기");
                lock.lockInterruptibly();  // 인터럽트 가능한 대기
                try {
                    System.out.println("  waiter: 락 획득");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("  waiter: 인터럽트 발생! 대기 취소");
            }
        });

        holder.start();
        waiter.start();

        sleep(500);
        System.out.println("  main: waiter 인터럽트!");
        waiter.interrupt();

        holder.join();
        waiter.join();

        System.out.println();
    }

    /**
     * 재진입성(Reentrancy): 같은 스레드가 이미 보유한 락을 다시 획득 가능
     * - synchronized도 재진입 가능
     * - getHoldCount()로 재진입 횟수 확인
     */
    private static void demonstrateReentrancy() {
        System.out.println("[재진입성 (Reentrancy)]");

        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        System.out.println("  첫 번째 lock - holdCount: " + lock.getHoldCount());
        try {
            lock.lock();  // 같은 스레드 - 재진입 성공
            System.out.println("  두 번째 lock - holdCount: " + lock.getHoldCount());
            try {
                lock.lock();
                System.out.println("  세 번째 lock - holdCount: " + lock.getHoldCount());
            } finally {
                lock.unlock();
                System.out.println("  unlock - holdCount: " + lock.getHoldCount());
            }
        } finally {
            lock.unlock();
            System.out.println("  unlock - holdCount: " + lock.getHoldCount());
        }
        lock.unlock();
        System.out.println("  unlock - holdCount: " + lock.getHoldCount());

        System.out.println("  -> lock 횟수만큼 unlock 필요!");
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