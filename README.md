# Java Core - 동시성 (Concurrency)

## 학습 목표

**단순 사용이 아닌 원리 이해**가 핵심입니다.

---

## 학습 로드맵

### Phase 1: 기초 개념

| 주제 | 핵심 내용 | 상태 |
|------|----------|------|
| Thread 기초 | Thread, Runnable, 생명주기 | ✅ |
| Thread 안전성 | Race Condition, Critical Section | ✅ |
| synchronized | Monitor Lock, 재진입성 | ✅ |
| volatile | 가시성, happens-before | ✅ |

### Phase 2: java.util.concurrent

| 주제 | 핵심 내용 | 상태 |
|------|----------|------|
| Executor Framework | ExecutorService, ThreadPool | ✅ |
| Future & Callable | 비동기 결과 처리, CompletionService, invokeAll/Any | ✅ |
| CompletableFuture | 비동기 파이프라인, 예외 처리, 작업 조합 | ✅ |
| Lock API | ReentrantLock, ReadWriteLock | ⬜ |

### Phase 3: 동시성 컬렉션 & Atomic

| 주제 | 핵심 내용 | 상태 |
|------|----------|------|
| ConcurrentHashMap | 세그먼트 락, CAS 기반 | ⬜ |
| BlockingQueue | Producer-Consumer 패턴 | ⬜ |
| Atomic 클래스 | CAS(Compare-And-Swap) 원리 | ⬜ |
| LongAdder/Accumulator | 고성능 카운터 | ⬜ |

### Phase 4: 심화 & 트러블슈팅

| 주제 | 핵심 내용 | 상태 |
|------|----------|------|
| Deadlock | 발생 조건, 탐지, 회피 | ⬜ |
| 성능 분석 | JVM 스레드 덤프 분석 | ⬜ |
| 실전 패턴 | Double-Checked Locking, ThreadLocal | ⬜ |

---

## 디렉토리 구조

```
java-core/
├── src/main/java/com/example/concurrency/
│   ├── basic/           # Thread 기초
│   ├── synchronization/ # synchronized, volatile
│   ├── executor/        # ExecutorService, Future
│   ├── lock/            # Lock API
│   ├── collection/      # 동시성 컬렉션
│   ├── atomic/          # Atomic 클래스
│   └── problem/         # 동시성 문제 예제
└── src/test/java/com/example/concurrency/
    └── ...              # 테스트 코드
```

---

## 면접 대비 핵심 질문

1. **synchronized vs ReentrantLock 차이점은?**
2. **volatile은 어떤 문제를 해결하는가?**
3. **ConcurrentHashMap은 어떻게 동시성을 보장하는가?**
4. **Deadlock 발생 조건 4가지는?**
5. **CAS(Compare-And-Swap)란?**
6. **ThreadLocal은 언제 사용하는가?**
7. **happens-before 관계란?**
