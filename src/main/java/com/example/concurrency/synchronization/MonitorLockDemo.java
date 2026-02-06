package com.example.concurrency.synchronization;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Monitor Lock 심화 - 재진입성(Reentrancy)과 가시성
 *
 * 핵심 개념:
 * 1. 재진입성(Reentrancy): 이미 Lock을 획득한 스레드가 같은 Lock을 다시 획득 가능
 * 2. Lock 카운터: 재진입할 때마다 카운터 증가, unlock 시 감소
 * 3. 가시성 보장: synchronized 블록 진입 시 메인 메모리에서 읽고, 나갈 때 메인 메모리에 쓰기
 *
 * 생각해볼 점: "synchronized가 재진입을 지원하지 않으면 어떤 문제가 발생하는가?"
 * -> 부모 클래스의 synchronized 메서드를 자식이 오버라이드하고 super 호출 시 데드락 발생
 *
 * 참고: synchronized의 내부 lock count는 JVM 내부 구현이라 직접 접근 불가
 *      ReentrantLock.getHoldCount()로 실제 lock count 확인 가능
 */
public class MonitorLockDemo {

    private final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Monitor Lock 심화 ===\n");

        demonstrateReentrancy();
        demonstrateSynchronizedReentrancy();
        demonstrateInheritanceReentrancy();
        demonstrateBlockedState();
    }

    /**
     * ReentrantLock으로 재진입성 예시 - 실제 lock count 확인
     */
    private static void demonstrateReentrancy() {
        System.out.println("[재진입성 예시 - ReentrantLock.getHoldCount()]");

        MonitorLockDemo demo = new MonitorLockDemo();
        demo.outerMethodWithLock();

        System.out.println("-> getHoldCount()로 실제 lock count 확인 가능\n");
    }

    public void outerMethodWithLock() {
        lock.lock();
        try {
            System.out.println("outerMethod: Lock 획득, holdCount=" + lock.getHoldCount());
            innerMethodWithLock();
            System.out.println("outerMethod: innerMethod 반환 후, holdCount=" + lock.getHoldCount());
        } finally {
            lock.unlock();
        }
        System.out.println("outerMethod: unlock 후, holdCount=" + lock.getHoldCount());
    }

    public void innerMethodWithLock() {
        lock.lock(); // 재진입 - 같은 스레드가 같은 Lock을 다시 획득
        try {
            System.out.println("  innerMethod: 재진입 성공, holdCount=" + lock.getHoldCount());
        } finally {
            lock.unlock();
            System.out.println("  innerMethod: unlock 후, holdCount=" + lock.getHoldCount());
        }
    }

    /**
     * synchronized도 동일하게 재진입 지원 (단, count 확인 불가)
     */
    private static void demonstrateSynchronizedReentrancy() {
        System.out.println("[synchronized 재진입 - 데드락 없이 동작 확인]");

        MonitorLockDemo demo = new MonitorLockDemo();
        demo.outerMethodSync();

        System.out.println("-> synchronized도 재진입 지원 (내부 count는 JVM이 관리)\n");
    }

    public synchronized void outerMethodSync() {
        System.out.println("outerMethod: synchronized 진입");
        innerMethodSync(); // 같은 Lock(this)을 다시 획득 시도 - 재진입
        System.out.println("outerMethod: 종료");
    }

    public synchronized void innerMethodSync() {
        System.out.println("  innerMethod: 재진입 성공 (데드락 없음)");
    }

    /**
     * 상속에서의 재진입: 자식이 부모의 synchronized 메서드 호출
     */
    private static void demonstrateInheritanceReentrancy() {
        System.out.println("[상속에서의 재진입 데모]");

        Child child = new Child();
        child.doSomething();

        System.out.println("-> 재진입 덕분에 super.doSomething() 호출 가능\n");
    }

    static class Parent {
        public synchronized void doSomething() {
            System.out.println("  Parent.doSomething()");
        }
    }

    static class Child extends Parent {
        @Override
        public synchronized void doSomething() {
            System.out.println("Child.doSomething() 시작");
            super.doSomething(); // 같은 Lock (this)을 다시 획득
            System.out.println("Child.doSomething() 종료");
        }
    }

    /**
     * BLOCKED 상태 데모: Lock을 기다리는 스레드
     */
    private static void demonstrateBlockedState() throws InterruptedException {
        System.out.println("[BLOCKED 상태 데모]");

        Object lock = new Object();

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                System.out.println("Holder: Lock 획득, 3초간 유지");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Holder: Lock 해제");
            }
        }, "Holder");

        Thread waiter = new Thread(() -> {
            System.out.println("Waiter: Lock 획득 시도...");
            synchronized (lock) {
                System.out.println("Waiter: Lock 획득 성공!");
            }
        }, "Waiter");

        holder.start();
        Thread.sleep(100); // Holder가 먼저 Lock 획득하도록

        waiter.start();
        Thread.sleep(100);

        // Waiter는 BLOCKED 상태
        System.out.println("Waiter 상태: " + waiter.getState()); // BLOCKED

        holder.join();
        waiter.join();

        System.out.println("-> Lock 대기 중인 스레드는 BLOCKED 상태\n");
    }
}
