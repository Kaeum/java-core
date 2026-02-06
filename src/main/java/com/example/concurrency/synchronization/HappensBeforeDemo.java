package com.example.concurrency.synchronization;

/**
 * happens-before 관계와 volatile의 실전 활용
 *
 * 핵심 개념:
 * 1. happens-before: volatile 쓰기 이전의 모든 작업이 -> volatile 읽기 이후에 보장됨
 * 2. 이 덕분에 volatile 변수 하나로 여러 일반 변수의 가시성까지 보장 가능
 * 3. 실전 활용: Double-Checked Locking 싱글톤 패턴
 *
 * 생각해볼 점: "DCL(Double-Checked Locking)에서 왜 volatile이 필요한가?"
 * -> 객체 생성은 (1)메모리 할당 (2)생성자 실행 (3)참조 할당 인데,
 *    reordering으로 (1)->(3)->(2) 순서가 되면 불완전한 객체가 보일 수 있음
 */
public class HappensBeforeDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== happens-before & DCL 데모 ===\n");

        //demonstrateHappensBefore();
        //demonstrateDCLProblem();
        demonstrateDCLWithVolatile();
    }

    // ========== happens-before 데모 ==========

    private static int data = 0;          // 일반 변수
    private static int extra = 0;         // 일반 변수
    private static volatile boolean ready = false;  // volatile 변수

    /**
     * volatile 쓰기 이전의 모든 일반 쓰기가, volatile 읽기 이후에 보장됨
     *
     * Thread 1: data=42, extra=100, ready=true (volatile 쓰기)
     * Thread 2: if(ready) -> data와 extra도 최신 값 보장
     */
    private static void demonstrateHappensBefore() throws InterruptedException {
        System.out.println("[happens-before 관계 데모]");

        int failCount = 0;
        int trials = 10000;

        for (int i = 0; i < trials; i++) {
            data = 0;
            extra = 0;
            ready = false;

            Thread writer = new Thread(() -> {
                data = 42;        // (1) 일반 쓰기
                extra = 100;      // (2) 일반 쓰기
                ready = true;     // (3) volatile 쓰기 -> (1),(2)를 flush
            });

            final int[] result = {-1, -1};
            Thread reader = new Thread(() -> {
                if (ready) {           // (4) volatile 읽기 -> (1),(2)도 보장
                    result[0] = data;  // (5) 42가 보장됨
                    result[1] = extra; // (6) 100이 보장됨
                }
            });

            writer.start();
            reader.start();
            writer.join();
            reader.join();

            // ready=true를 봤는데 data나 extra가 0이면 happens-before 위반
            if (result[0] != -1 && (result[0] != 42 || result[1] != 100)) {
                failCount++;
            }
        }

        System.out.println("시행 횟수: " + trials);
        System.out.println("happens-before 위반: " + failCount + "회");
        System.out.println("-> volatile 쓰기 이전의 일반 쓰기도 volatile 읽기 이후에 보장됨\n");
    }

    // ========== Double-Checked Locking ==========

    /**
     * 문제 있는 DCL (volatile 없음)
     *
     * 객체 생성 과정:
     * 1. 메모리 할당
     * 2. 생성자 실행 (필드 초기화)
     * 3. 참조를 변수에 할당
     *
     * JVM이 reordering하면: 1 -> 3 -> 2
     * -> 다른 스레드가 3 이후, 2 이전에 접근하면 불완전한 객체를 봄
     */
    static class UnsafeSingleton {
        private static UnsafeSingleton instance; // volatile 아님!
        private int value;

        private UnsafeSingleton() {
            value = 42; // 생성자에서 초기화
        }

        public static UnsafeSingleton getInstance() {
            if (instance == null) {           // 1st check (no lock)
                synchronized (UnsafeSingleton.class) {
                    if (instance == null) {   // 2nd check (with lock)
                        instance = new UnsafeSingleton();
                        // reordering 시: 참조 할당 후 생성자 미완료 가능
                    }
                }
            }
            return instance;
        }

        public int getValue() { return value; }
    }

    /**
     * 올바른 DCL (volatile 사용)
     */
    static class SafeSingleton {
        private static volatile SafeSingleton instance; // volatile!
        private int value;

        private SafeSingleton() {
            value = 42;
        }

        public static SafeSingleton getInstance() {
            if (instance == null) {           // 1st check (no lock)
                synchronized (SafeSingleton.class) {
                    if (instance == null) {   // 2nd check (with lock)
                        instance = new SafeSingleton();
                        // volatile 쓰기 -> 생성자 완료 후에만 참조가 보임
                    }
                }
            }
            return instance;
            // volatile 읽기 -> 객체의 모든 필드가 초기화된 상태 보장
        }

        public int getValue() { return value; }
    }

    private static void demonstrateDCLProblem() throws InterruptedException {
        System.out.println("[DCL 문제 시나리오 설명]");
        System.out.println("  Thread A: instance = new Singleton()  (reorder 가능)");
        System.out.println("    1. 메모리 할당");
        System.out.println("    3. instance = 할당된 주소  ← reorder!");
        System.out.println("    2. 생성자 실행 (아직 안 됨)");
        System.out.println("  Thread B: if(instance != null) -> instance.getValue()");
        System.out.println("    -> 생성자 미완료 객체 접근! (value=0)");
        System.out.println();
    }

    private static void demonstrateDCLWithVolatile() throws InterruptedException {
        System.out.println("[volatile로 DCL 해결]");

        int trials = 100;
        int numThreads = 10;
        int successCount = 0;

        for (int trial = 0; trial < trials; trial++) {
            // SafeSingleton의 instance를 매 시행마다 리셋
            // (테스트를 위해 리플렉션 사용)
            try {
                var field = SafeSingleton.class.getDeclaredField("instance");
                field.setAccessible(true);
                field.set(null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Thread[] threads = new Thread[numThreads];
            SafeSingleton[] results = new SafeSingleton[numThreads];

            for (int i = 0; i < numThreads; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    results[idx] = SafeSingleton.getInstance();
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            // 모든 스레드가 같은 인스턴스를 받았는지, value가 42인지 확인
            boolean allSame = true;
            boolean allCorrect = true;
            for (int i = 0; i < numThreads; i++) {
                if (results[i] != results[0]) allSame = false;
                if (results[i].getValue() != 42) allCorrect = false;
            }
            if (allSame && allCorrect) successCount++;
        }

        System.out.println("시행 횟수: " + trials);
        System.out.println("싱글톤 보장 성공: " + successCount + "/" + trials);
        System.out.println("-> volatile이 reordering을 방지하여 완전한 객체만 보임\n");

        System.out.println("=== 정리 ===");
        System.out.println("volatile은 단순 가시성뿐 아니라 happens-before로");
        System.out.println("주변 변수의 정합성까지 보장하는 '울타리(fence)' 역할");
    }
}
