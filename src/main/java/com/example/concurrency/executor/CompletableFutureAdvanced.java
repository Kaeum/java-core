package com.example.concurrency.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CompletableFuture 심화 - 예외 처리 & 작업 조합
 *
 * 예외 처리:
 * - exceptionally: 예외 시 대체값 반환 (catch와 유사)
 * - handle: 성공/실패 모두 처리 (try-catch-finally)
 * - whenComplete: 결과 변경 없이 처리 (finally)
 *
 * 작업 조합:
 * - thenCompose: 순차 연결 (flatMap) - 의존적 작업
 * - thenCombine: 병렬 후 합치기 - 독립적 작업
 * - allOf: 모든 작업 완료 대기
 * - anyOf: 하나라도 완료되면 반환
 */
public class CompletableFutureAdvanced {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CompletableFuture 심화 ===\n");

        //demonstrateExceptionally();
        //demonstrateHandle();
        //demonstrateWhenComplete();
        demonstrateThenCompose();
        demonstrateThenCombine();
        demonstrateAllOfAnyOf();
    }

    /**
     * exceptionally: 예외 발생 시 대체값 반환
     */
    private static void demonstrateExceptionally() throws Exception {
        System.out.println("[exceptionally - 예외 시 대체값]");

        // 정상 케이스: exceptionally 스킵
        String normal = CompletableFuture
            .supplyAsync(() -> "정상 결과")
            .exceptionally(ex -> "대체값")
            .get();
        System.out.println("  정상: " + normal);

        // 예외 케이스: exceptionally 실행
        String error = CompletableFuture
            .<String>supplyAsync(() -> {
                throw new RuntimeException("에러 발생!");
            })
            .exceptionally(ex -> {
                System.out.println("  예외 처리: " + ex.getMessage());
                return "대체값";
            })
            .get();
        System.out.println("  예외 후: " + error);

        System.out.println();
    }

    /**
     * handle: 성공/실패 모두 처리 (BiFunction<T, Throwable, U>)
     */
    private static void demonstrateHandle() throws Exception {
        System.out.println("[handle - 성공/실패 모두 처리]");

        // 성공 시: result 있음, exception null
        String success = CompletableFuture
            .supplyAsync(() -> "성공")
            .handle((result, ex) -> {
                if (ex != null) {
                    return "실패: " + ex.getMessage();
                }
                return "처리됨: " + result;
            })
            .get();
        System.out.println("  " + success);

        // 실패 시: result null, exception 있음
        String failure = CompletableFuture
            .<String>supplyAsync(() -> {
                throw new RuntimeException("문제 발생");
            })
            .handle((result, ex) -> {
                if (ex != null) {
                    return "실패: " + ex.getCause().getMessage();
                }
                return "처리됨: " + result;
            })
            .get();
        System.out.println("  " + failure);

        System.out.println();
    }

    /**
     * whenComplete: 결과를 변경하지 않고 부수 효과만 실행
     * - handle과 달리 결과를 바꾸지 않음
     * - 로깅, 알림 등에 적합
     */
    private static void demonstrateWhenComplete() throws Exception {
        System.out.println("[whenComplete - 결과 변경 없이 처리]");

        String result = CompletableFuture
            .supplyAsync(() -> "원본 결과")
            .whenComplete((r, ex) -> {
                // 로깅 등 부수효과
                System.out.println("  whenComplete 호출됨: " + r);
            })
            .get();
        System.out.println("  최종 결과: " + result);  // 원본 그대로

        System.out.println();
    }

    /**
     * thenCompose: 순차적 의존 작업 연결 (flatMap)
     *
     * thenApply vs thenCompose:
     * - thenApply(fn): fn이 일반 값 반환 -> CompletableFuture<T>
     * - thenCompose(fn): fn이 CompletableFuture 반환 -> 평탄화됨
     */
    private static void demonstrateThenCompose() throws Exception {
        System.out.println("[thenCompose - 순차 의존 작업]");

        // 시나리오: userId로 User 조회 -> User의 주문 조회
        CompletableFuture<String> orderFuture = getUserAsync(1L)
            .thenCompose(user -> getOrderAsync(user));  // User -> CompletableFuture<Order>

        System.out.println("  " + orderFuture.get());

        // thenApply를 쓰면 중첩됨 (CompletableFuture<CompletableFuture<T>>)
        // thenCompose는 평탄화 (CompletableFuture<T>)

        System.out.println();
    }

    /**
     * thenCombine: 두 독립 작업 병렬 실행 후 결과 합치기
     */
    private static void demonstrateThenCombine() throws Exception {
        System.out.println("[thenCombine - 병렬 후 합치기]");

        long start = System.currentTimeMillis();

        CompletableFuture<String> priceFuture = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "가격: 10000원";
        });

        CompletableFuture<String> stockFuture = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "재고: 5개";
        });

        // 두 작업이 병렬로 실행되고, 둘 다 완료되면 합침
        String combined = priceFuture
            .thenCombine(stockFuture, (price, stock) -> price + ", " + stock)
            .get();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  결과: " + combined);
        System.out.println("  소요 시간: " + elapsed + "ms (병렬이라 ~1초)");

        System.out.println();
    }

    /**
     * allOf / anyOf: 여러 CompletableFuture 조합
     */
    private static void demonstrateAllOfAnyOf() throws Exception {
        System.out.println("[allOf / anyOf]");

        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return "작업1";
        });
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "작업2";
        });
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "작업3";
        });

        // allOf: 모든 작업 완료 대기 (반환값은 Void)
        long start = System.currentTimeMillis();
        CompletableFuture.allOf(f1, f2, f3).get();
        System.out.println("  allOf 완료 (소요: " + (System.currentTimeMillis() - start) + "ms)");
        System.out.println("  결과: " + f1.get() + ", " + f2.get() + ", " + f3.get());

        // anyOf: 하나라도 완료되면 반환
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "느림";
        });
        CompletableFuture<String> faster = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "빠름";
        });

        start = System.currentTimeMillis();
        Object first = CompletableFuture.anyOf(fast, faster).get();
        System.out.println("  anyOf 완료: " + first + " (소요: " + (System.currentTimeMillis() - start) + "ms)");

        System.out.println();
    }

    // 헬퍼 메서드
    private static CompletableFuture<String> getUserAsync(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "User-" + userId;
        });
    }

    private static CompletableFuture<String> getOrderAsync(String user) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return user + "의 주문: Order-123";
        });
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}