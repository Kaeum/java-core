package com.example.concurrency.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LockTest {

    // === ReentrantLock ===

    @Test
    @DisplayName("ReentrantLock으로 동시성 문제를 해결한다")
    void reentrantLockEnsuresThreadSafety() throws Exception {
        Lock lock = new ReentrantLock();
        AtomicInteger counter = new AtomicInteger(0);

        int threadCount = 100;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    lock.lock();
                    try {
                        counter.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * incrementsPerThread, counter.get());
    }

    @Test
    @DisplayName("tryLock은 락 획득 실패 시 false를 반환한다")
    void tryLockReturnsFalseWhenLockNotAvailable() throws Exception {
        Lock lock = new ReentrantLock();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        // 락을 보유하는 스레드
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockAcquired.countDown();
                try {
                    testDone.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                lock.unlock();
            }
        });
        holder.start();

        lockAcquired.await();

        // 락 획득 시도 -> 실패
        boolean acquired = lock.tryLock();
        assertFalse(acquired);

        testDone.countDown();
        holder.join();
    }

    @Test
    @DisplayName("tryLock(timeout)은 지정 시간 동안 대기한다")
    void tryLockWithTimeoutWaitsForDuration() throws Exception {
        Lock lock = new ReentrantLock();

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                sleep(500);
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        sleep(50);  // holder가 락 획득하도록

        long start = System.currentTimeMillis();
        boolean acquired = lock.tryLock(200, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(acquired);
        assertTrue(elapsed >= 180 && elapsed < 400);

        holder.join();
    }

    @Test
    @DisplayName("ReentrantLock은 재진입이 가능하다")
    void reentrantLockAllowsReentry() {
        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        assertEquals(1, lock.getHoldCount());

        lock.lock();  // 재진입
        assertEquals(2, lock.getHoldCount());

        lock.unlock();
        assertEquals(1, lock.getHoldCount());

        lock.unlock();
        assertEquals(0, lock.getHoldCount());
    }

    @Test
    @DisplayName("lockInterruptibly는 인터럽트 시 예외를 던진다")
    void lockInterruptiblyThrowsOnInterrupt() throws Exception {
        Lock lock = new ReentrantLock();
        lock.lock();  // main이 락 보유

        Thread waiter = new Thread(() -> {
            try {
                lock.lockInterruptibly();
                fail("InterruptedException이 발생해야 함");
            } catch (InterruptedException e) {
                // 예상된 동작
            }
        });

        waiter.start();
        sleep(100);  // waiter가 대기 상태가 되도록

        waiter.interrupt();
        waiter.join();

        lock.unlock();
    }

    // === ReadWriteLock ===

    @Test
    @DisplayName("여러 스레드가 readLock을 동시에 획득할 수 있다")
    void multipleThreadsCanAcquireReadLock() throws Exception {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        AtomicInteger concurrentReaders = new AtomicInteger(0);
        AtomicInteger maxConcurrentReaders = new AtomicInteger(0);

        int readerCount = 5;
        CountDownLatch allStarted = new CountDownLatch(readerCount);
        CountDownLatch allDone = new CountDownLatch(readerCount);

        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                rwLock.readLock().lock();
                try {
                    int current = concurrentReaders.incrementAndGet();
                    maxConcurrentReaders.updateAndGet(max -> Math.max(max, current));
                    allStarted.countDown();
                    sleep(200);
                } finally {
                    concurrentReaders.decrementAndGet();
                    rwLock.readLock().unlock();
                    allDone.countDown();
                }
            }).start();
        }

        allStarted.await();
        allDone.await();

        // 동시에 여러 reader가 있었음
        assertTrue(maxConcurrentReaders.get() > 1);
    }

    @Test
    @DisplayName("writeLock 보유 시 readLock은 대기해야 한다")
    void readLockBlocksWhileWriteLockHeld() throws Exception {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        List<String> events = new ArrayList<>();

        Thread writer = new Thread(() -> {
            rwLock.writeLock().lock();
            try {
                events.add("write-start");
                sleep(300);
                events.add("write-end");
            } finally {
                rwLock.writeLock().unlock();
            }
        });

        Thread reader = new Thread(() -> {
            sleep(50);  // writer가 먼저 락 획득하도록
            events.add("read-waiting");
            rwLock.readLock().lock();
            try {
                events.add("read-acquired");
            } finally {
                rwLock.readLock().unlock();
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        // reader는 writer가 끝난 후에야 락 획득
        assertEquals("write-start", events.get(0));
        assertEquals("read-waiting", events.get(1));
        assertEquals("write-end", events.get(2));
        assertEquals("read-acquired", events.get(3));
    }

    @Test
    @DisplayName("readLock 보유 시 writeLock은 대기해야 한다")
    void writeLockBlocksWhileReadLockHeld() throws Exception {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        List<String> events = new ArrayList<>();

        Thread reader = new Thread(() -> {
            rwLock.readLock().lock();
            try {
                events.add("read-start");
                sleep(300);
                events.add("read-end");
            } finally {
                rwLock.readLock().unlock();
            }
        });

        Thread writer = new Thread(() -> {
            sleep(50);
            events.add("write-waiting");
            rwLock.writeLock().lock();
            try {
                events.add("write-acquired");
            } finally {
                rwLock.writeLock().unlock();
            }
        });

        reader.start();
        writer.start();
        reader.join();
        writer.join();

        assertEquals("read-start", events.get(0));
        assertEquals("write-waiting", events.get(1));
        assertEquals("read-end", events.get(2));
        assertEquals("write-acquired", events.get(3));
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}