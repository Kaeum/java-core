package com.example.concurrency.problem;

import java.util.concurrent.TimeUnit;

/**
 * Deadlock (교착 상태) 데모
 *
 * Deadlock 발생 조건 4가지 (모두 동시에 충족되어야 함):
 * 1. Mutual Exclusion (상호 배제): 자원을 한 스레드만 사용 가능
 * 2. Hold and Wait (보유 대기): 자원을 보유한 채 다른 자원을 기다림
 * 3. No Preemption (비선점): 다른 스레드의 자원을 강제로 빼앗을 수 없음
 * 4. Circular Wait (순환 대기): A->B->A 형태의 순환 대기 발생
 *
 * -> 4가지 중 하나라도 깨면 Deadlock은 발생하지 않는다.
 *
 * 생각해볼 점: "Deadlock 발생 조건 4가지를 설명하고, 실무에서 어떻게 예방하는가?"
 */
public class DeadlockDemo {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Deadlock 데모 ===\n");

        demonstrateDeadlock();
    }

    /**
     * 전형적인 Deadlock 시나리오
     *
     * Thread-1: LOCK_A 획득 -> LOCK_B 대기
     * Thread-2: LOCK_B 획득 -> LOCK_A 대기
     * -> 순환 대기 발생 -> Deadlock
     */
    public static void demonstrateDeadlock() throws InterruptedException {
        System.out.println("[Deadlock 발생 시나리오]");
        System.out.println("Thread-1: LOCK_A -> LOCK_B 순서로 획득 시도");
        System.out.println("Thread-2: LOCK_B -> LOCK_A 순서로 획득 시도");
        System.out.println();

        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("  Thread-1: LOCK_A 획득");
                sleep(100); // Thread-2가 LOCK_B를 획득할 시간

                System.out.println("  Thread-1: LOCK_B 획득 시도...");
                synchronized (LOCK_B) {
                    System.out.println("  Thread-1: LOCK_B 획득 (여기 도달 불가)");
                }
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("  Thread-2: LOCK_B 획득");
                sleep(100); // Thread-1이 LOCK_A를 획득할 시간

                System.out.println("  Thread-2: LOCK_A 획득 시도...");
                synchronized (LOCK_A) {
                    System.out.println("  Thread-2: LOCK_A 획득 (여기 도달 불가)");
                }
            }
        }, "Thread-2");

        t1.start();
        t2.start();

        // 3초 기다려서 Deadlock 상태 확인
        t1.join(3000);
        t2.join(3000);

        if (t1.isAlive() && t2.isAlive()) {
            System.out.println("\n  -> Deadlock 발생! 두 스레드 모두 블로킹 상태");
            System.out.println("  -> t1 state: " + t1.getState()); // BLOCKED
            System.out.println("  -> t2 state: " + t2.getState()); // BLOCKED

            // 데모이므로 강제 종료 (실무에서는 이렇게 하면 안 됨)
            // Deadlock 상태의 스레드는 interrupt로도 풀리지 않음 (synchronized는 인터럽트 무시)
            System.out.println("\n  [참고] synchronized Deadlock은 interrupt로 풀 수 없음");
            System.out.println("  -> ReentrantLock의 lockInterruptibly()를 사용해야 함");
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
