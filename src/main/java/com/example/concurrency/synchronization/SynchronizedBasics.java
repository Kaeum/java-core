package com.example.concurrency.synchronization;

/**
 * synchronized 키워드 기초
 *
 * 핵심 개념:
 * 1. Monitor Lock (=Intrinsic Lock, 고유 락): 모든 Java 객체가 가지는 암묵적 잠금
 * 2. synchronized는 Monitor Lock을 사용하여 임계 영역(Critical Section)을 보호
 * 3. 한 번에 하나의 스레드만 Lock을 획득하여 임계 영역 진입 가능
 *
 * 생각해볼 점: "synchronized의 동작 원리는?"
 * -> 객체의 Monitor Lock을 획득한 스레드만 임계 영역에 진입 가능
 * -> Lock을 획득하지 못한 스레드는 BLOCKED 상태로 대기
 */
public class SynchronizedBasics {

    private int count = 0;
    private static int staticCount = 0;

    /**
     * 1. synchronized 메서드 (인스턴스 메서드)
     * - Lock 대상: this (현재 인스턴스)
     * - 같은 인스턴스의 모든 synchronized 메서드는 같은 Lock 공유
     */
    public synchronized void incrementSync() {
        count++;
    }

    /**
     * 2. synchronized 블록
     * - Lock 대상: 지정한 객체 (여기서는 this)
     * - 메서드 전체가 아닌 필요한 부분만 동기화 가능 (성능 이점)
     */
    public void incrementBlock() {
        // Lock이 필요 없는 작업...

        synchronized (this) {
            count++; // Critical Section
        }

        // Lock이 필요 없는 작업...
    }

    /**
     * 3. synchronized static 메서드
     * - Lock 대상: Class 객체 (SynchronizedBasics.class)
     * - 모든 인스턴스가 같은 Lock 공유
     */
    public static synchronized void incrementStatic() {
        staticCount++;
    }

    /**
     * 4. synchronized 블록 (Class Lock)
     * - static 메서드와 동일한 Lock 사용
     */
    public void incrementStaticBlock() {
        synchronized (SynchronizedBasics.class) {
            staticCount++;
        }
    }

    public int getCount() {
        return count;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== synchronized 기초 데모 ===\n");

        // 인스턴스 Lock vs 클래스 Lock 비교
        demonstrateInstanceLock();
        demonstrateClassLock();
        demonstrateDifferentLocks();
    }

    /**
     * 인스턴스 Lock: 같은 객체의 synchronized 메서드는 상호 배제
     */
    private static void demonstrateInstanceLock() throws InterruptedException {
        System.out.println("[Instance Lock 테스트]");

        SynchronizedBasics obj = new SynchronizedBasics();
        int iterations = 100000;

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

        System.out.println("기대값: " + (iterations * 2));
        System.out.println("실제값: " + obj.getCount());
        System.out.println("-> 같은 인스턴스의 synchronized는 같은 Lock 공유\n");
    }

    /**
     * 클래스 Lock: static synchronized는 클래스 단위 Lock
     */
    private static void demonstrateClassLock() throws InterruptedException {
        System.out.println("[Class Lock 테스트]");

        staticCount = 0;
        int iterations = 100000;

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) incrementStatic();
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                synchronized (SynchronizedBasics.class) {
                    staticCount++;
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("기대값: " + (iterations * 2));
        System.out.println("실제값: " + staticCount);
        System.out.println("-> static synchronized와 synchronized(Class)는 같은 Lock\n");
    }

    /**
     * 다른 Lock 객체 사용 시: 동기화되지 않음!
     */
    private static void demonstrateDifferentLocks() throws InterruptedException {
        System.out.println("[다른 Lock 객체 테스트 - 주의!]");

        SynchronizedBasics obj1 = new SynchronizedBasics();
        SynchronizedBasics obj2 = new SynchronizedBasics();

        // 공유 카운터 (의도적으로 문제 발생시키기 위해)
        int[] sharedCount = {0};
        int iterations = 100000;

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                synchronized (obj1) { // obj1 Lock
                    sharedCount[0]++;
                }
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                synchronized (obj2) { // obj2 Lock (다른 Lock!)
                    sharedCount[0]++;
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("기대값: " + (iterations * 2));
        System.out.println("실제값: " + sharedCount[0]);
        System.out.println("-> 다른 객체로 동기화하면 Race Condition 발생!");
        System.out.println("-> 공유 자원 접근 시 반드시 같은 Lock 객체 사용해야 함\n");
    }
}
