package com.example.concurrency.synchronization;

/**
 * volatile 키워드 - 가시성(Visibility) 보장
 *
 * 핵심 개념:
 * 1. 가시성: CPU 캐시가 아닌 메인 메모리에서 직접 읽고 쓰기
 * 2. happens-before: volatile 쓰기 이전의 모든 작업이 읽는 스레드에게 보임
 * 3. 원자성 미보장: volatile은 가시성만 해결, count++는 여전히 unsafe
 *
 * 면접 질문: "volatile과 synchronized의 차이점은?"
 * -> volatile: 가시성만 보장, 원자성 X, Lock-free
 * -> synchronized: 가시성 + 원자성, Lock 기반
 */
public class VolatileDemo {

    // volatile 없음 - 가시성 문제 발생 가능
    private static boolean flagWithoutVolatile = false;

    // volatile 있음 - 가시성 보장
    private static volatile boolean flagWithVolatile = false;

    // volatile이어도 원자성 미보장
    private static volatile int volatileCount = 0;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== volatile 데모 ===\n");

        demonstrateVisibilityProblem();
       // demonstrateVolatileFix();
        //demonstrateAtomicityNotGuaranteed();
    }

    /**
     * 가시성 문제: volatile 없이 flag 변경이 안 보일 수 있음
     *
     * 주의: JVM 최적화에 따라 문제가 발생하거나 안 할 수 있음
     *       -server 모드, JIT 컴파일 후 더 잘 재현됨
     */
    private static void demonstrateVisibilityProblem() throws InterruptedException {
        System.out.println("[가시성 문제 데모 - volatile 없음]");

        flagWithoutVolatile = false;

        Thread reader = new Thread(() -> {
            int count = 0;
            while (!flagWithoutVolatile) {
                count++;  // busy-wait (CPU가 최적화로 flag를 캐시할 수 있음)
            }
            System.out.println("Reader: flag 변경 감지! 반복 횟수: " + count);
        });

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(100);  // reader가 먼저 루프에 진입하도록
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Writer: flag를 true로 변경");
            flagWithoutVolatile = true;
        });

        reader.start();
        writer.start();

        // 2초 타임아웃 (가시성 문제 발생 시 reader가 무한루프)
        reader.join(2000);
        writer.join();

        if (reader.isAlive()) {
            System.out.println("⚠️ Reader가 아직 실행 중 - 가시성 문제 발생!");
            System.out.println("   (flag 변경을 못 보고 무한루프)");
            reader.interrupt();  // 강제 종료
        }
        System.out.println();
    }

    /**
     * volatile로 가시성 문제 해결
     */
    private static void demonstrateVolatileFix() throws InterruptedException {
        System.out.println("[volatile로 가시성 해결]");

        flagWithVolatile = false;

        Thread reader = new Thread(() -> {
            int count = 0;
            while (!flagWithVolatile) {  // volatile 읽기 - 항상 메인 메모리에서
                count++;
            }
            System.out.println("Reader: flag 변경 감지! 반복 횟수: " + count);
        });

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Writer: flag를 true로 변경 (volatile)");
            flagWithVolatile = true;  // volatile 쓰기 - 메인 메모리에 즉시 반영
        });

        reader.start();
        writer.start();

        reader.join(2000);
        writer.join();

        if (reader.isAlive()) {
            System.out.println("⚠️ 예상치 못한 문제 발생");
            reader.interrupt();
        } else {
            System.out.println("✅ volatile 덕분에 가시성 보장됨");
        }
        System.out.println();
    }

    /**
     * volatile은 원자성을 보장하지 않음
     * count++는 Read-Modify-Write 연산이라 여전히 Race Condition 발생
     */
    private static void demonstrateAtomicityNotGuaranteed() throws InterruptedException {
        System.out.println("[volatile은 원자성 미보장]");

        volatileCount = 0;
        int numThreads = 10;
        int incrementsPerThread = 10000;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    volatileCount++;  // NOT atomic! (Read -> Modify -> Write)
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expected = numThreads * incrementsPerThread;
        System.out.println("기대값: " + expected);
        System.out.println("실제값: " + volatileCount);
        System.out.println("손실: " + (expected - volatileCount));
        System.out.println("-> volatile이어도 count++는 Race Condition 발생!");
        System.out.println("-> 원자성 필요 시: synchronized 또는 AtomicInteger 사용\n");
    }
}
