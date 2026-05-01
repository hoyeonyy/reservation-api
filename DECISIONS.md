# DECISIONS.md

설계 과정에서 고민했던 핵심 기술 쟁점과 그 선택의 근거를 기록합니다.

---

## 쟁점 1. 재고 정합성 — 동시 차감 요청을 어떻게 원자적으로 처리할 것인가

### 상황

자정 프로모션 시 500~1000 TPS가 동시에 같은 상품의 재고 차감을 요청합니다.
"재고 확인 → 차감" 사이에 다른 요청이 끼어들면 초과판매가 발생합니다.

### 검토한 선택지

| 방법 | 문제점 |
|---|---|
| DB Optimistic Lock (`@Version`) | 고경합 환경에서 대부분의 요청이 버전 충돌로 실패 → Retry Storm 유발, 처리량 급격히 저하 |
| DB Pessimistic Lock (`SELECT FOR UPDATE`) | 트랜잭션 내부에 결제 API 호출이 포함되면 커넥션 점유 시간이 수 초 → 커넥션 풀 고갈 |
| Redis Distributed Lock (Redisson RLock) | 락 획득·해제 2회 Redis 왕복 필요, Lua Script 대비 느리고 복잡 |
| **Redis Lua Script + DB UPDATE** | **채택** |

### 최종 선택과 이유

**Redis Lua Script(빠른 거부 게이트) + DB `UPDATE WHERE remaining_stock > 0`(source of truth)** 조합을 채택했습니다.

```
[정상 경로]
요청 → Redis Lua Script
        stock <= 0  → 즉시 409 반환 (DB까지 가지 않음, 부하 차단)
        stock >  0  → 통과
                       ↓
                   DB: UPDATE products
                       SET remaining_stock = remaining_stock - 1
                       WHERE id = ? AND remaining_stock > 0
                       affected rows == 0 → 409
                       affected rows == 1 → 예약 진행
```

핵심 판단 포인트 두 가지:

**1) DB가 source of truth입니다.**
Redis Lua Script의 역할을 "재고 숫자 관리자"가 아니라 "빠른 거부 게이트"로 제한했습니다.
DB의 `UPDATE WHERE remaining_stock > 0`은 InnoDB 행 락 하에서 원자적으로 실행되므로,
Redis가 stale하거나 중간에 장애가 발생해도 DB 레벨에서 초과판매를 최종 차단합니다.

**2) Lua Script는 Redis 단일 스레드에서 원자적으로 실행됩니다.**
서버 2대에서 동시에 요청이 들어와도 Redis에 도달하는 순서대로 처리됩니다.
`SELECT FOR UPDATE` 없이도 공정한 중앙 판단 지점 역할을 수행합니다.

Lua Script 반환값 처리:

| 반환값 | 의미 | 처리 |
|---|---|---|
| -1 (stock <= 0) | 품절 | 즉시 409, DB 미호출 |
| -2 (nil, 미초기화) | Redis 초기화 안 됨 | DB 조회 후 `SETNX` 초기화, 재시도 |
| >= 0 | 통과 | DB UPDATE 실행 |

**수용한 트레이드오프**: Redis DECR과 DB UPDATE 사이의 짧은 구간에서 Redis 장애가 발생하면 Redis가 실제보다 1개 많다고 인식할 수 있습니다. 그러나 DB가 source of truth이므로 초과판매는 발생하지 않고, Redis 복구 시 DB 값으로 재동기화합니다.

---

## 쟁점 2. 결제 트랜잭션 분리 — DB 커넥션 고갈을 어떻게 막을 것인가

### 상황

외부 결제 API 호출은 3~10초가 소요됩니다. 이를 `@Transactional` 내부에 넣으면:

```
@Transactional
public void book() {
    UPDATE remaining_stock        // DB 커넥션 점유 시작
    INSERT booking (RESERVED)
    외부 결제 API 호출 (3~10초)   // 이 시간 동안 커넥션 묶임
    UPDATE booking (CONFIRMED)
}
```

500 TPS 환경에서는 커넥션 풀이 즉시 고갈됩니다.

### 검토한 선택지

| 방법 | 트레이드오프 |
|---|---|
| Outbox 패턴 | 서버 다운 시에도 완벽한 정합성 보장, 그러나 outbox 테이블 + 폴링 스케줄러 + exactly-once 처리 구현 복잡도 과다 |
| `@Async` 비동기 실행 | 클라이언트 계약 변경(즉시 응답 불가 → 202 Accepted + 폴링 API 필요), 스케줄러 경합 구간 확대, ThreadPoolTaskExecutor 별도 설정 필요 |
| **`@TransactionalEventListener(AFTER_COMMIT)`** | **채택** |

### 최종 선택과 이유

트랜잭션을 두 단계로 분리하는 `@TransactionalEventListener(AFTER_COMMIT)` 패턴을 채택했습니다.

```
[트랜잭션 1] ~수 ms
  UPDATE products remaining_stock - 1 WHERE remaining_stock > 0
  INSERT booking  (status = RESERVED)
  INSERT payments (status = PENDING)   ← 결제 수단/금액 영속화
  PaymentRequestEvent 발행
COMMIT → DB 커넥션 즉시 반납

↓ @TransactionalEventListener(phase = AFTER_COMMIT) — 동기 실행

외부 결제 API 호출 (DB 커넥션 점유 없음)

성공: [트랜잭션 2] UPDATE booking → CONFIRMED, payments → SUCCESS
실패: [보상 트랜잭션] UPDATE booking → FAILED, remaining_stock + 1, payments → FAILED
```

결제 API 호출 구간에 DB 커넥션을 점유하지 않으므로 커넥션 풀 고갈이 발생하지 않습니다.

**`@Async` 미적용 이유**: 현재 API 계약은 단건 요청에 CONFIRMED/FAILED 최종 응답을 반환합니다. 비동기 전환 시 클라이언트에 202 Accepted를 반환하고 폴링 API를 별도로 추가해야 하는데, 이는 현재 요구사항 범위를 벗어납니다. 동기 실행의 스레드 블로킹 트레이드오프보다 API 계약의 단순성을 우선했습니다.

**수용한 트레이드오프**: 트랜잭션 1 COMMIT 후 서버가 다운되면 Spring 인메모리 이벤트가 유실됩니다. 이 케이스는 쟁점 6의 BookingScheduler가 복구합니다.

---

## 쟁점 3. 멱등성 — 중복 결제 요청을 어떻게 차단할 것인가

### 상황

네트워크 오류로 클라이언트가 같은 요청을 재전송할 수 있습니다.
더 심각한 케이스는 자정 트래픽 집중 시 사용자가 버튼을 빠르게 여러 번 클릭하여 아주 짧은 간격으로 동일 요청이 서버에 동시에 도달하는 경우입니다.

### 검토한 선택지

| 방법 | 문제점 |
|---|---|
| DB UNIQUE constraint만 사용 | 동시 중복 차단 가능하나, 충돌 시 에러 응답 → 클라이언트가 실패로 오해. Redis 없어도 동작하지만 충돌 처리가 불명확 |
| Redis 캐시만 사용 | Redis 장애 시 멱등성 보장 불가, 중복 결제 가능 |
| Redis 3계층 방어 (캐시 + SETNX + DB UNIQUE) | 과잉 설계. TTL 관리 복잡, Redis 장애 시 동작이 애매해짐 |
| **Redisson 분산락 + DB UNIQUE + DataIntegrityViolationException catch** | **채택** |

### 최종 선택과 이유

멱등성 문제를 두 케이스로 분리해서 각각 처리합니다:

| 케이스 | 처리 방법 |
|---|---|
| 이미 완료된 요청의 재시도 | `checkIdempotency()` — DB EXISTS 체크 후 기존 booking 반환 |
| 처리 중 동시 중복 요청 진입 | Redisson 분산락 (`waitTime=0`) — 락 획득 실패 즉시 409 |
| Redis 장애로 락 획득 불가 + 동시 중복 | DB UNIQUE constraint → `DataIntegrityViolationException` catch → 기존 booking 조회 후 반환 |

```
요청 진입
    ↓
Redisson 락 tryLock(waitTime=0)
    획득 실패 → 409 (동시 중복 즉시 차단)
    획득 성공 (또는 Redis 장애로 락 없이 진행)
    ↓
checkIdempotency(key)
    exists → 기존 booking 반환 (200 OK)
    not exists → 실제 처리
    ↓
booking INSERT
    DataIntegrityViolationException → 기존 booking 조회 후 반환 (200 OK)
    정상 → 진행
```

`waitTime=0`을 선택한 이유: 동시 중복 요청을 "즉시 차단"하는 것이 목적이므로 대기 시간이 없어야 합니다. waitTime을 두면 그 시간 동안 스레드를 점유하여 고트래픽 환경에서 낭비가 발생합니다.

Redis 장애 시 분산락이 없어도 DB UNIQUE가 최후 방어선이 되어 중복 예약 자체는 막힙니다. `DataIntegrityViolationException`을 catch하여 에러가 아닌 기존 결과를 반환함으로써 완전한 멱등성을 유지합니다.

---

## 쟁점 4. 결제 확장성 — 새 결제수단 추가 시 기존 코드를 수정하지 않으려면

### 상황

현재 CREDIT_CARD, Y_PAY, Y_POINT 3가지 결제수단을 지원하며, 복합 결제(신용카드+포인트, Y페이+포인트)도 가능해야 합니다. 향후 새 결제수단이 추가되어도 예약 비즈니스 로직을 수정하지 않아야 합니다(OCP 원칙).

### 검토한 선택지

| 방법 | 문제점                                                 |
|---|-----------------------------------------------------|
| if/switch 분기 | 새 결제수단 추가 시 BookingService 수정 필요, OCP 위반            |
| PaymentStrategy + Factory 패턴 | 단일 결제에는 적합하나, 복합 결제 조합/롤백 책임이 BookingService에 집중    |
| CompositePaymentStrategy | Factory + Composite 클래스 별도 필요, 구조 복잡                |
| 2PC (Pre-authorize → Capture) | 외부 PG API의 pre-auth 지원 보장 불가, Y_POINT에도 예약 개념 추가 필요 |
| Saga 패턴 | 이벤트 기반 보상 트랜잭션 - 오버 엔지니어링                           |
| **Chargeable 공통 인터페이스 + PaymentService 오케스트레이션** | **채택**                                              |

### 최종 선택과 이유

```java
// 공통 인터페이스
public interface Chargeable {
    PaymentResult pay(PaymentContext context);
    void refund(PaymentResult result);
    PaymentMethod supportedMethod();
}

// @Component 구현체 — Spring이 자동 수집
CreditCardChargeable  → 외부 PG API 호출
YPayChargeable        → 외부 PG API 호출
YPointChargeable      → 외부 PG API 호출 (MockPaymentGateway 경유)

// PaymentService — 오케스트레이터
// 생성자에서 List<Chargeable> 주입 → Map<PaymentMethod, Chargeable> 구성
// validate(): CREDIT_CARD + Y_PAY 조합 금지
// pay(): 순차 실행, 실패 시 역순 rollback()

// BookingService
paymentService.pay(userId, bookingId, requests)  // 이게 전부
```

**복합 결제 실패 롤백 흐름**:
```
CREDIT_CARD 성공 → Y_POINT 실패 감지
→ 역순 rollback: Y_POINT(실패했으니 skip), CREDIT_CARD.refund()
→ PaymentFailedException throw
→ 보상 트랜잭션: booking FAILED + 재고 복구
```

**새 결제수단 추가 시**: `NewChargeable implements Chargeable` 파일 하나만 추가하면 Spring이 자동으로 `chargeableMap`에 등록합니다. BookingService, PaymentService 수정이 없습니다.

RetryablePaymentException / NonRetryablePaymentException을 분리하여 스케줄러가 재시도 가능/불가 케이스를 구분할 수 있게 했습니다.

---

## 쟁점 5. Redis 장애 대응 

### 상황

쟁점 2(Sorted Set 대기열 제거)와 쟁점 3(Redis 멱등성 캐시 제거) 결정으로,
실제 Redis 장애 시 영향받는 범위가 **재고 Lua Script 하나**로 좁혀졌습니다.
Redis가 죽으면 재고 빠른거부 게이트가 실패합니다.

### 검토한 선택지

| 방법 | 문제점 |
|---|---|
| 수동 try/catch fallback | Redis 타임아웃(2000ms) 동안 스레드 블로킹. 1000 TPS 환경에서 Redis가 느려지면 모든 요청이 2초씩 대기 → 장애 전파 |
| **Circuit Breaker (Resilience4j)** | **채택** |

### 최종 선택과 이유

```
try/catch: Redis 죽음 → 모든 요청 2초 대기 후 fallback (장애 전파)
CB OPEN:   Redis 죽음 → 즉시 fallback (장애 격리)
```

Circuit Breaker는 OPEN 상태에서 Redis에 요청 자체를 보내지 않아 타임아웃 대기 없이 즉시 DB fallback으로 전환합니다.

**CB 설정값**:
```yaml
resilience4j.circuitbreaker.instances.redisInventory:
  sliding-window-size: 10
  failure-rate-threshold: 50        # 10건 중 5건 실패 시 OPEN
  wait-duration-in-open-state: 10s  # 10초 후 HALF-OPEN
  permitted-number-of-calls-in-half-open-state: 3
  ignore-exceptions:
    - SoldOutException               # 재고 소진은 Redis 장애가 아님
```

`SoldOutException`을 ignore-exceptions에 등록한 이유: 재고가 정상적으로 소진된 상황을 Redis 장애로 카운팅하면 CB가 오작동합니다.

**장애 경로 흐름**:
```
[CB OPEN — Redis 장애]
요청 → Redis 건너뜀 → 즉시 DB UPDATE WHERE remaining_stock > 0

[CB HALF-OPEN — 복구 감지]
소수 요청으로 Redis 상태 확인
성공 시: SET inventory:stock:{id} = DB remaining_stock  (재동기화)
         CB CLOSED 전환
```

**수용한 트레이드오프**: CB OPEN 중에는 Redis 단일 스레드가 아닌 InnoDB 행 락 경쟁으로 처리되어 도착 순서 보장이 약화됩니다(랜덤 경쟁). 단, 단일 유저의 중복 이득 차단(멱등성)은 CB OPEN 중에도 유지됩니다. 서버 안정성과 완전한 공정성은 동시에 달성할 수 없는 트레이드오프로 판단하고, 서버 안정성을 우선했습니다.

---

## 쟁점 6. RESERVED stuck 예약 처리 — 서버 다운 시 결제 유실을 어떻게 복구할 것인가

### 상황

`@TransactionalEventListener(AFTER_COMMIT)`은 Spring 인메모리 이벤트를 사용합니다.
트랜잭션 1 COMMIT 후 서버가 죽으면 이벤트가 유실되어 RESERVED 상태로 재고가 영구 묶입니다.

```
TX1 COMMIT → Booking RESERVED, Payments PENDING (DB에 저장됨)
    ↓
서버 다운 → PaymentEventHandler 실행 안 됨
    ↓
RESERVED 상태로 영구 방치 → 재고 묶임, 결제 미처리
```

### 검토한 선택지

| 방법 | 문제점 |
|---|---|
| 운영 모니터링 수동 처리 | 자정 1000건 프로모션에서 서버 다운 시 대량 stuck 발생, 수동 처리 현실적이지 않음 |
| **PENDING Payment 조회 + BookingScheduler** | **채택** |

### 최종 선택과 이유

트랜잭션 1에서 `Payment(status=PENDING)`을 저장하는 것을 스케줄러 복구의 앵커 포인트로 활용했습니다.

별도 outbox 테이블을 만들지 않은 이유: "어떤 결제 수단으로 얼마를 결제할지"는 Payment 테이블에 이미 저장되어 있습니다. JSON 컬럼을 bookings에 추가하거나 별도 outbox 테이블을 만들면 payment 정보의 중복 저장이 발생합니다.

```
[BookingScheduler — 1분마다 실행]
PENDING 상태 Payment 조회 (5분 이상 경과 기준)
    ↓
bookingId별로 그룹핑
    ↓
paymentRetryCount >= 3 → failPayment() (최종 실패, 재고 반납)
paymentRetryCount < 3  → paymentService.pay() 재시도
    성공 → confirmPayment() → CONFIRMED
    NonRetryablePaymentException → failPayment()
    RetryablePaymentException    → incrementRetryCount()
```

**분산 환경 중복 실행 방지 — ShedLock 도입**:

2대 이상의 서버가 동시에 스케줄러를 실행하면 같은 예약에 대해 결제가 중복 처리될 수 있습니다. 이를 막기 위해 ShedLock을 도입했습니다.

ShedLock의 락 저장소로 MySQL을 선택한 이유:
- Redis를 락 저장소로 쓰면 Redis 장애 시 스케줄러도 중단됨 — 복구해야 할 시점에 복구 스케줄러가 동작하지 않는 역설
- MySQL은 이미 프로젝트 핵심 인프라이므로 추가 의존성이 없음
- `shedlock` 테이블 하나만 추가하면 됩니다

```yaml
@SchedulerLock(name = "retryPendingPayments",
               lockAtMostFor = "PT55S",   # 최대 55초 — 스케줄 주기(60s) 이내 해제 보장
               lockAtLeastFor = "PT30S")  # 최소 30초 — 빠른 완료 후 재실행 방지
```

**수용한 트레이드오프**: PaymentEventHandler(정상 경로)와 BookingScheduler(복구 경로)가 같은 예약을 동시에 처리하는 짧은 경합 구간이 존재합니다. 정상 경로에서는 PaymentEventHandler가 수 초 내에 처리를 완료하므로 5분 threshold 이전에 완료됩니다. 결제사 멱등성(동일 bookingId 재시도 시 중복 결제 없음)에 의존하여 이 경합을 수용했습니다.

---

## 쟁점 7. Redis-DB 불일치 보상 — 트랜잭션 롤백 시 Redis 재고를 어떻게 복구할 것인가

### 상황

`InventoryProcessor.decrementStock()`은 Redis DECR과 DB UPDATE를 순차 실행합니다.
Redis DECR이 먼저 실행된 후 DB UPDATE가 포함된 트랜잭션이 롤백되면, Redis만 감소하고 DB는 복구되어 불일치가 발생합니다.

### 선택

`StockDecrementedEvent` + `@TransactionalEventListener(phase = AFTER_ROLLBACK)` 조합을 채택했습니다.

```
InventoryProcessor.redisDecrement()
  → Redis DECRBY 실행
  → StockDecrementedEvent 발행 (Spring 이벤트)

[트랜잭션 정상 커밋] → AFTER_ROLLBACK 핸들러 실행 안 됨 (무시)

[트랜잭션 롤백] → AFTER_ROLLBACK 핸들러 실행
  → Redis INCRBY 1 (Redis 재고 복구)
```

이 방식의 장점은 Redis 복구 로직이 비즈니스 로직과 완전히 분리된다는 점입니다. `BookingProcessor`나 `InventoryProcessor`는 롤백 여부를 신경 쓰지 않아도 됩니다.

결제 실패 보상 트랜잭션(`failPayment`)에서도 `inventoryProcessor.incrementStock()`을 호출하여 DB와 Redis를 함께 복구합니다.

---


## 쟁점 8. Redis 재고 초기화 — Pre-warm과 SETNX 선택

### 상황

서버 재시작 시 Redis에 재고 키가 없으면 Lua Script가 -2를 반환하고 그때마다 DB를 조회해서 초기화합니다. 1000 TPS 환경에서 초기 요청들이 일제히 DB 조회를 트리거하면 불필요한 부하가 발생합니다.

### 선택

`ApplicationRunner`를 구현한 `InventoryPreWarmer`로 서버 시작 시 모든 상품의 재고를 Redis에 미리 적재합니다.

**`SET` 대신 `SETNX`(`setIfAbsent`)를 사용한 이유**: 2대 서버가 동시에 재시작하면 양쪽이 모두 Pre-warm을 실행합니다. `SET`을 사용하면 한 서버의 Pre-warm이 다른 서버가 이미 적재한(그리고 이미 일부 차감된) 값을 덮어쓸 수 있습니다. `setIfAbsent`는 키가 이미 존재하면 덮어쓰지 않아 이 경쟁을 안전하게 처리합니다.

---

## 사용한 주요 라이브러리 선택 근거 요약

| 라이브러리 | 선택 근거 |
|---|---|
| **Redisson** | Redis 분산락(`RLock`) 구현을 위해 도입. Spring Data Redis가 제공하지 않는 tryLock/waitTime API가 필요. `redisson-spring-boot-starter`로 `spring.data.redis.*` 설정 그대로 재사용 가능 |
| **Resilience4j** | Circuit Breaker 구현. Spring Boot 3.x와 공식 통합(`resilience4j-spring-boot3`), `@CircuitBreaker` 어노테이션 기반 선언적 적용 가능 |
| **Flyway** | 스키마 버전 관리. `ddl-auto: validate`와 조합하여 Hibernate가 스키마를 생성하지 않고 검증만 수행, 의도치 않은 스키마 변경 방지 |
| **ShedLock** | 분산 스케줄러 중복 실행 방지. Redis가 아닌 JDBC(MySQL) provider 선택 — Redis 장애 시에도 스케줄러가 동작해야 하기 때문 |
