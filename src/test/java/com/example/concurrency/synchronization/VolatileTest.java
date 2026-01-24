package com.example.concurrency.synchronization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VolatileTest {

    @Test
    @DisplayName("volatile 변수는 가시성을 보장한다")
    void volatileGuaranteesVisibility() throws InterruptedException {
        var holder = new Object() {
            volatile boolean flag = false;
        };

        Thread writer = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            holder.flag = true;
        });

        Thread reader = new Thread(() -> {
            while (!holder.flag) {
                // busy-wait
            }
        });

        reader.start();
        writer.start();

        reader.join(2000); // volatile이면 2초 내에 반드시 종료
        writer.join();

        assertFalse(reader.isAlive(), "volatile 변수는 가시성을 보장해야 한다");
    }

    @Test
    @DisplayName("volatile은 원자성을 보장하지 않는다 - count++에서 손실 발생")
    void volatileDoesNotGuaranteeAtomicity() throws InterruptedException {
        var counter = new Object() {
            volatile int count = 0;
        };

        int numThreads = 50;
        int iterations = 10000;

        // 여러 번 시도해서 한 번이라도 손실이 발생하면 원자성 미보장 증명
        boolean lossDetected = false;
        int expected = numThreads * iterations;

        for (int trial = 0; trial < 5; trial++) {
            counter.count = 0;
            Thread[] threads = new Thread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < iterations; j++) {
                        counter.count++;
                    }
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            if (counter.count < expected) {
                lossDetected = true;
                break;
            }
        }

        assertTrue(lossDetected,
                "volatile count++는 Race Condition으로 손실이 발생해야 한다");
    }

    @Test
    @DisplayName("happens-before: volatile 쓰기 이전의 일반 쓰기도 보장")
    void happensBefore() throws InterruptedException {
        var shared = new Object() {
            int data = 0;        // 일반 변수
            volatile boolean ready = false;  // volatile 변수
        };

        int failCount = 0;

        for (int i = 0; i < 5000; i++) {
            shared.data = 0;
            shared.ready = false;

            final int[] result = {-1};

            Thread writer = new Thread(() -> {
                shared.data = 42;      // 일반 쓰기
                shared.ready = true;   // volatile 쓰기 (fence)
            });

            Thread reader = new Thread(() -> {
                if (shared.ready) {         // volatile 읽기
                    result[0] = shared.data; // happens-before에 의해 42 보장
                }
            });

            writer.start();
            reader.start();
            writer.join();
            reader.join();

            // ready=true를 봤는데 data가 42가 아니면 happens-before 위반
            if (result[0] != -1 && result[0] != 42) {
                failCount++;
            }
        }

        assertEquals(0, failCount, "happens-before 위반이 발생하면 안 된다");
    }

}
