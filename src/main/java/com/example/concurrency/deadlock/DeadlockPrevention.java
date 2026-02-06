package com.example.concurrency.deadlock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Deadlock 회피 전략
 *
 * 4가지 발생 조건 중 하나를 깨면 Deadlock을 예방할 수 있다.
 *
 * 전략 1: Lock Ordering (순환 대기 제거)
 *   - 모든 스레드가 같은 순서로 락 획득
 *   - 가장 실용적이고 흔한 방법
 *   - System.identityHashCode() 등으로 일관된 순서 결정
 *
 * 전략 2: tryLock with Timeout (보유 대기 제거)
 *   - 일정 시간 내 획득 실패 시 보유한 락도 해제
 *   - ReentrantLock 필요 (synchronized는 불가)
 *
 * 전략 3: lockInterruptibly (비선점 우회)
 *   - 외부에서 인터럽트로 대기 중인 스레드를 깨울 수 있음
 *   - ReentrantLock 필요
 *
 * 생각해볼 점: "Deadlock을 예방하기 위한 설계 전략은?"
 */
public class DeadlockPrevention {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Deadlock 회피 전략 ===\n");

        demonstrateLockOrdering();
        demonstrateTryLockTimeout();
        demonstrateLockInterruptibly();
    }

    // ========================================
    // 전략 1: Lock Ordering
    // ========================================

    /**
     * Lock Ordering: 항상 같은 순서로 락을 획득하면 순환 대기가 발생하지 않는다.
     *
     * 핵심: 객체의 hash 값으로 락 순서를 결정.
     * identityHashCode가 같은 극히 드문 경우를 대비해 tieBreaker 락을 사용한다.
     */
    private static final Object tieBreakerLock = new Object();

    public static void transferWithOrdering(Account from, Account to, int amount) {
        // identityHashCode로 일관된 순서 결정
        int fromHash = System.identityHashCode(from.lock);
        int toHash = System.identityHashCode(to.lock);

        Object firstLock;
        Object secondLock;

        if (fromHash < toHash) {
            firstLock = from.lock;
            secondLock = to.lock;
        } else if (fromHash > toHash) {
            firstLock = to.lock;
            secondLock = from.lock;
        } else {
            // hash 충돌 시 tieBreaker로 순서 보장
            synchronized (tieBreakerLock) {
                synchronized (from.lock) {
                    synchronized (to.lock) {
                        executeTransfer(from, to, amount);
                    }
                }
            }
            return;
        }

        synchronized (firstLock) {
            synchronized (secondLock) {
                executeTransfer(from, to, amount);
            }
        }
    }

    private static void executeTransfer(Account from, Account to, int amount) {
        if (from.balance >= amount) {
            from.balance -= amount;
            to.balance += amount;
        }
    }

    private static void demonstrateLockOrdering() throws InterruptedException {
        System.out.println("[전략 1: Lock Ordering]");

        Account accountA = new Account("A", 1000);
        Account accountB = new Account("B", 1000);

        // 양방향 송금을 동시에 해도 Deadlock 발생하지 않음
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                transferWithOrdering(accountA, accountB, 1);
            }
        }, "transfer-A-to-B");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                transferWithOrdering(accountB, accountA, 1);
            }
        }, "transfer-B-to-A");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("  A 잔액: " + accountA.balance);
        System.out.println("  B 잔액: " + accountB.balance);
        System.out.println("  합계: " + (accountA.balance + accountB.balance) + " (원래: 2000)");
        System.out.println("  -> Lock Ordering으로 Deadlock 없이 완료\n");
    }

    // ========================================
    // 전략 2: tryLock with Timeout
    // ========================================

    /**
     * tryLock: 일정 시간 내 락 획득 실패 시 보유한 락을 해제하고 재시도.
     *
     * 장점: Deadlock 자체가 발생하지 않음
     * 단점: Livelock 가능성 (모든 스레드가 동시에 해제 -> 재시도 반복)
     * -> 랜덤 백오프로 완화
     */
    public static boolean transferWithTryLock(
            LockAccount from, LockAccount to, int amount, long timeoutMs) {

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        while (System.nanoTime() < deadline) {
            if (from.lock.tryLock()) {
                try {
                    if (to.lock.tryLock()) {
                        try {
                            if (from.balance >= amount) {
                                from.balance -= amount;
                                to.balance += amount;
                            }
                            return true;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }

            // 랜덤 백오프: Livelock 방지
            sleep(1 + (long) (Math.random() * 5));
        }

        return false; // 타임아웃
    }

    private static void demonstrateTryLockTimeout() throws InterruptedException {
        System.out.println("[전략 2: tryLock with Timeout]");

        LockAccount accountA = new LockAccount("A", 1000);
        LockAccount accountB = new LockAccount("B", 1000);

        Thread t1 = new Thread(() -> {
            int success = 0;
            for (int i = 0; i < 1000; i++) {
                if (transferWithTryLock(accountA, accountB, 1, 100)) {
                    success++;
                }
            }
            System.out.println("  t1 성공: " + success + "/1000");
        }, "tryLock-A->B");

        Thread t2 = new Thread(() -> {
            int success = 0;
            for (int i = 0; i < 1000; i++) {
                if (transferWithTryLock(accountB, accountA, 1, 100)) {
                    success++;
                }
            }
            System.out.println("  t2 성공: " + success + "/1000");
        }, "tryLock-B->A");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("  A 잔액: " + accountA.balance);
        System.out.println("  B 잔액: " + accountB.balance);
        System.out.println("  합계: " + (accountA.balance + accountB.balance) + " (원래: 2000)");
        System.out.println("  -> tryLock으로 Deadlock 없이 완료\n");
    }

    // ========================================
    // 전략 3: lockInterruptibly
    // ========================================

    /**
     * lockInterruptibly: Deadlock 상태에 빠진 스레드를 외부에서 interrupt로 깨울 수 있다.
     *
     * synchronized의 한계:
     * - synchronized로 락 대기 중인 스레드는 interrupt해도 반응하지 않는다.
     * - 즉, synchronized Deadlock은 외부에서 해소할 방법이 없다.
     *
     * lockInterruptibly의 동작:
     * - 락 대기 중 interrupt가 걸리면 InterruptedException을 던지며 대기에서 빠져나온다.
     * - 이를 통해 Deadlock 상황을 외부에서 감지 후 해소할 수 있다.
     */
    private static void demonstrateLockInterruptibly() throws InterruptedException {
        System.out.println("[전략 3: lockInterruptibly]");

        Lock lockA = new ReentrantLock();
        Lock lockB = new ReentrantLock();

        // t1: lockA 획득 -> lockB를 lockInterruptibly로 대기
        Thread t1 = new Thread(() -> {
            try {
                lockA.lock();
                System.out.println("  t1: lockA 획득");
                sleep(100);

                System.out.println("  t1: lockB를 lockInterruptibly로 대기...");
                lockB.lockInterruptibly();
                try {
                    System.out.println("  t1: lockB 획득 (Deadlock 해소 후 도달)");
                } finally {
                    lockB.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("  t1: InterruptedException 발생! 대기 탈출");
            } finally {
                lockA.unlock();
                System.out.println("  t1: lockA 해제");
            }
        }, "interruptibly-t1");

        // t2: lockB 획득 -> lockA를 lockInterruptibly로 대기
        Thread t2 = new Thread(() -> {
            try {
                lockB.lock();
                System.out.println("  t2: lockB 획득");
                sleep(100);

                System.out.println("  t2: lockA를 lockInterruptibly로 대기...");
                lockA.lockInterruptibly();
                try {
                    System.out.println("  t2: lockA 획득 (Deadlock 해소 후 도달)");
                } finally {
                    lockA.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("  t2: InterruptedException 발생! 대기 탈출");
            } finally {
                lockB.unlock();
                System.out.println("  t2: lockB 해제");
            }
        }, "interruptibly-t2");

        t1.start();
        t2.start();

        // Deadlock 진입 대기
        sleep(500);

        // t1을 interrupt -> t1이 lockB 대기에서 탈출 -> lockA 해제 -> t2가 lockA 획득 가능
        System.out.println("  main: t1을 interrupt하여 Deadlock 해소");
        t1.interrupt();

        t1.join(2000);
        t2.join(2000);

        boolean resolved = !t1.isAlive() && !t2.isAlive();
        System.out.println("  Deadlock 해소: " + resolved);
        System.out.println("  -> synchronized였다면 interrupt가 무시되어 해소 불가\n");
    }

    // ========================================
    // 내부 클래스
    // ========================================

    /**
     * synchronized 기반 계좌 (Lock Ordering용)
     */
    public static class Account {

        final String name;
        final Object lock = new Object();
        int balance;

        public Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    /**
     * ReentrantLock 기반 계좌 (tryLock용)
     */
    public static class LockAccount {

        final String name;
        final Lock lock = new ReentrantLock();
        int balance;

        public LockAccount(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
