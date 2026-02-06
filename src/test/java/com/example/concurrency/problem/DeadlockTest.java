package com.example.concurrency.problem;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeadlockTest {

    // === Deadlock 탐지 ===

    @Test
    @DisplayName("ThreadMXBean으로 Deadlock을 탐지할 수 있다")
    void detectDeadlockWithThreadMXBean() throws Exception {
        Object lockA = new Object();
        Object lockB = new Object();

        CountDownLatch bothLocked = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                bothLocked.countDown();
                awaitQuietly(bothLocked);
                synchronized (lockB) {
                    // 도달 불가
                }
            }
        }, "deadlock-t1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                bothLocked.countDown();
                awaitQuietly(bothLocked);
                synchronized (lockA) {
                    // 도달 불가
                }
            }
        }, "deadlock-t2");

        t1.start();
        t2.start();

        // 두 스레드가 각각 첫 번째 락을 획득한 후 Deadlock에 진입할 시간
        Thread.sleep(500);

        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = mxBean.findDeadlockedThreads();

        assertNotNull(deadlocked, "Deadlock이 탐지되어야 한다");
        // 같은 JVM 내 다른 테스트의 잔존 deadlock 스레드가 있을 수 있으므로 >= 2
        assertTrue(deadlocked.length >= 2, "최소 2개 이상의 deadlock 스레드가 탐지되어야 한다");

        // Deadlock 상태의 스레드는 BLOCKED
        assertEquals(Thread.State.BLOCKED, t1.getState());
        assertEquals(Thread.State.BLOCKED, t2.getState());
    }

    // === Lock Ordering ===

    @Test
    @DisplayName("Lock Ordering으로 양방향 송금 시 Deadlock이 발생하지 않는다")
    void lockOrderingPreventsDeadlock() throws Exception {
        DeadlockPrevention.Account accountA = new DeadlockPrevention.Account("A", 10_000);
        DeadlockPrevention.Account accountB = new DeadlockPrevention.Account("B", 10_000);

        int transferCount = 5000;
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // A -> B 송금
        executor.submit(() -> {
            for (int i = 0; i < transferCount; i++) {
                DeadlockPrevention.transferWithOrdering(accountA, accountB, 1);
            }
            latch.countDown();
        });

        // B -> A 송금 (반대 방향)
        executor.submit(() -> {
            for (int i = 0; i < transferCount; i++) {
                DeadlockPrevention.transferWithOrdering(accountB, accountA, 1);
            }
            latch.countDown();
        });

        // 5초 내 완료되어야 함 (Deadlock이면 영원히 대기)
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Deadlock 없이 완료되어야 한다");

        // 총합 보존 확인 (돈이 증발하거나 복제되면 안 됨)
        assertEquals(20_000, accountA.balance + accountB.balance,
                "송금 전후 총합이 동일해야 한다");
    }

    @Test
    @DisplayName("Lock Ordering은 다수 스레드에서도 안전하다")
    void lockOrderingIsSafeWithManyThreads() throws Exception {
        DeadlockPrevention.Account[] accounts = new DeadlockPrevention.Account[5];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new DeadlockPrevention.Account("acc-" + i, 1000);
        }

        int threadCount = 10;
        int transfersPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int i = 0; i < transfersPerThread; i++) {
                    int from = (int) (Math.random() * accounts.length);
                    int to = (int) (Math.random() * accounts.length);
                    if (from != to) {
                        DeadlockPrevention.transferWithOrdering(
                                accounts[from], accounts[to], 1);
                    }
                }
                latch.countDown();
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "모든 스레드가 Deadlock 없이 완료되어야 한다");

        int totalBalance = 0;
        for (DeadlockPrevention.Account acc : accounts) {
            totalBalance += acc.balance;
        }
        assertEquals(5000, totalBalance, "전체 잔액 합계가 보존되어야 한다");
    }

    // === tryLock with Timeout ===

    @Test
    @DisplayName("tryLock으로 양방향 송금 시 Deadlock이 발생하지 않는다")
    void tryLockPreventsDeadlock() throws Exception {
        DeadlockPrevention.LockAccount accountA = new DeadlockPrevention.LockAccount("A", 10_000);
        DeadlockPrevention.LockAccount accountB = new DeadlockPrevention.LockAccount("B", 10_000);

        int transferCount = 5000;
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean anyTimeout = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            for (int i = 0; i < transferCount; i++) {
                if (!DeadlockPrevention.transferWithTryLock(accountA, accountB, 1, 100)) {
                    anyTimeout.set(true);
                }
            }
            latch.countDown();
        });

        executor.submit(() -> {
            for (int i = 0; i < transferCount; i++) {
                if (!DeadlockPrevention.transferWithTryLock(accountB, accountA, 1, 100)) {
                    anyTimeout.set(true);
                }
            }
            latch.countDown();
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "tryLock은 Deadlock 없이 완료되어야 한다");

        // 총합 보존
        assertEquals(20_000, accountA.balance + accountB.balance,
                "송금 전후 총합이 동일해야 한다");
    }

    @Test
    @DisplayName("tryLock은 타임아웃 시 false를 반환한다")
    void tryLockReturnsFalseOnTimeout() throws Exception {
        DeadlockPrevention.LockAccount from = new DeadlockPrevention.LockAccount("from", 100);
        DeadlockPrevention.LockAccount to = new DeadlockPrevention.LockAccount("to", 100);

        // 별도 스레드에서 to의 락을 점유 (ReentrantLock 재진입 방지)
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            to.lock.lock();
            try {
                lockHeld.countDown();
                awaitQuietly(testDone);
            } finally {
                to.lock.unlock();
            }
        }, "lock-holder");
        holder.start();
        lockHeld.await();

        // from -> to 송금 시도: to의 락을 다른 스레드가 보유 중이므로 타임아웃
        boolean result = DeadlockPrevention.transferWithTryLock(from, to, 10, 200);
        assertFalse(result, "락 획득 실패 시 false를 반환해야 한다");

        // 잔액 변동 없음
        assertEquals(100, from.balance);
        assertEquals(100, to.balance);

        testDone.countDown();
        holder.join();
    }

    // === Deadlock 모니터 ===

    @Test
    @DisplayName("모니터 스레드가 Deadlock을 감지한다")
    void monitorThreadDetectsDeadlock() throws Exception {
        AtomicBoolean detected = new AtomicBoolean(false);
        Object lockA = new Object();
        Object lockB = new Object();

        // 커스텀 모니터: 감지 시 플래그 세팅
        Thread monitor = new Thread(() -> {
            ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while (!Thread.currentThread().isInterrupted()) {
                long[] ids = mxBean.findDeadlockedThreads();
                if (ids != null) {
                    detected.set(true);
                    return;
                }
                sleep(50);
            }
        }, "test-monitor");
        monitor.setDaemon(true);
        monitor.start();

        // Deadlock 생성
        CountDownLatch bothLocked = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                bothLocked.countDown();
                awaitQuietly(bothLocked);
                synchronized (lockB) { /* 도달 불가 */ }
            }
        });

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                bothLocked.countDown();
                awaitQuietly(bothLocked);
                synchronized (lockA) { /* 도달 불가 */ }
            }
        });

        t1.start();
        t2.start();

        // 모니터가 감지할 시간
        Thread.sleep(500);

        assertTrue(detected.get(), "모니터 스레드가 Deadlock을 감지해야 한다");
        monitor.interrupt();
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
