package com.example.concurrency.problem;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

/**
 * Deadlock 탐지 방법
 *
 * 1. ThreadMXBean (프로그래밍 방식)
 *    - findDeadlockedThreads(): synchronized + ReentrantLock 모두 탐지
 *    - findMonitorDeadlockedThreads(): synchronized만 탐지
 *    - 주기적으로 호출하여 모니터링 가능
 *
 * 2. jstack (운영 환경)
 *    - jstack <pid> 명령어로 스레드 덤프 생성
 *    - "Found one Java-level deadlock" 메시지로 확인
 *    - 어떤 스레드가 어떤 락을 보유/대기하는지 표시
 *
 * 3. JConsole / VisualVM (GUI 도구)
 *    - Threads 탭에서 "Detect Deadlock" 버튼
 *    - 그래프로 순환 대기 시각화
 *
 * 생각해볼 점: "운영 중인 서비스에서 Deadlock이 의심될 때 어떻게 진단하는가?"
 */
public class DeadlockDetection {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Deadlock 탐지 데모 ===\n");

        // Deadlock 상황 생성
        createDeadlock();

        // 1초 후 탐지 시도
        sleep(1000);
        detectDeadlock();
    }

    /**
     * ThreadMXBean을 사용한 Deadlock 탐지
     *
     * 실무에서는 별도 모니터링 스레드(데몬)로 주기적 실행하거나,
     * Spring Actuator 같은 헬스체크에 통합한다.
     */
    public static boolean detectDeadlock() {
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();

        // findDeadlockedThreads(): synchronized + Lock 모두 탐지
        long[] deadlockedThreadIds = mxBean.findDeadlockedThreads();

        if (deadlockedThreadIds == null) {
            System.out.println("[탐지 결과] Deadlock 없음");
            return false;
        }

        System.out.println("[탐지 결과] Deadlock 발견! 관련 스레드 " + deadlockedThreadIds.length + "개\n");

        ThreadInfo[] threadInfos = mxBean.getThreadInfo(deadlockedThreadIds, true, true);
        for (ThreadInfo info : threadInfos) {
            System.out.println("스레드: " + info.getThreadName() + " (id=" + info.getThreadId() + ")");
            System.out.println("  상태: " + info.getThreadState());
            System.out.println("  대기 중인 락: " + info.getLockName());
            System.out.println("  락 소유자: " + info.getLockOwnerName());
            System.out.println("  스택 트레이스:");

            StackTraceElement[] stackTrace = info.getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                System.out.println("    at " + stackTrace[i]);
            }
            System.out.println();
        }

        return true;
    }

    /**
     * 주기적 Deadlock 모니터링 스레드 생성
     *
     * 실무 패턴: 데몬 스레드로 일정 주기마다 Deadlock 체크.
     * 발견 시 로그 남기고 알림 전송.
     */
    public static Thread createMonitorThread(long checkIntervalMs) {
        Thread monitor = new Thread(() -> {
            ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while (!Thread.currentThread().isInterrupted()) {
                long[] ids = mxBean.findDeadlockedThreads();
                if (ids != null) {
                    System.out.println("[ALERT] Deadlock 감지! 스레드 수: " + ids.length);
                    // 실무: 로그 기록, 알림 전송, 스레드 덤프 저장
                }
                sleep(checkIntervalMs);
            }
        }, "deadlock-monitor");
        monitor.setDaemon(true);
        return monitor;
    }

    private static void createDeadlock() {
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                sleep(100);
                synchronized (LOCK_B) {
                    System.out.println("t1 완료");
                }
            }
        }, "worker-1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                sleep(100);
                synchronized (LOCK_A) {
                    System.out.println("t2 완료");
                }
            }
        }, "worker-2");

        t1.start();
        t2.start();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
