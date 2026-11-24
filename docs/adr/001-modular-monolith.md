# ADR-001. 모듈러 모놀리스와 동기 호출 목록

- **상태**: 승인
- **날짜**: 2026-11-23
- **결정자**: 사용자 / **작성자**: Claude Code(Claude Opus 5.5)
- **관련**: 작업 ID R0-05-001(이슈 #17), E05·E45(소속 모듈 참고), 불변식 I3·I4, 기획서 §4-3·§9-4, 작업진행.md B0·B1·B3~B6·A5-8

## 맥락
- 기획서 §4-3은 "모듈러 모놀리스 + 경계 강제 + Outbox(비동기 경계에만)"를 정했고, R0에서 모듈 간 **동기 호출 목록**과 분리 기준을 ADR로 남기라고 했다. MSA·Kubernetes는 하지 않는다(§9-4).
- 모듈 목록은 작업진행.md B1에 있다. 경계를 지키는 도구는 "ArchUnit 또는 Spring Modulith"로 열려 있었다.
- R0-06(모듈 패키지, ArchUnit 규칙)보다 먼저 정해야 한다. 호출 방향이 정해져야 패키지와 규칙을 만들 수 있다.
- 결제 흐름은 한 트랜잭션 안에서 여러 모듈을 거친다. 주문 생성 + 재고 예약 + 결제 시도 생성(B5-1), 조건부 `CONFIRMING` 전이 + 재예약 + paymentKey 저장(B5-3-2)이 그렇다. 그래서 모듈 간 호출을 이벤트로 바꿀 수 없는 곳이 있다(B0-1).

## 결정
**Spring Boot 앱 하나에 B1의 모듈을 두고, 모듈 간 호출은 아래 표에 있는 방향만 허용한다. 순환은 허용하지 않는다. 경계는 ArchUnit 하나로 CI에서 강제한다.**

**호출 방식**
- 다른 모듈은 그 모듈의 **공개 API 패키지**로만 호출한다. 다른 모듈의 엔티티·리포지토리는 직접 쓰지 않는다. 패키지 이름은 R0-06에서 정한다.
- 모듈 사이에는 JPA 연관을 걸지 않고 ID(`Long`)로 참조한다.
- 동기 호출은 호출한 쪽의 트랜잭션에 참여할 수 있다. DB가 하나라서 가능하고, B5-1·B5-3-2가 이를 필요로 한다. PG 호출은 트랜잭션 밖에서 한다(B5-4).
- 비동기 경계(Outbox)는 R2a에서 이벤트 목록 ADR로 따로 정한다.

**동기 호출 목록** (왼쪽이 오른쪽을 호출한다)

| 호출하는 모듈 → 호출받는 모듈 | 용도 | 도입 |
|---|---|---|
| `admin` → 모든 모듈 | 관리자 API 진입점. 상품·재고 관리, 출고 처리, 주문 조회 | R1 |
| `claim` → `order` | 조건부 전이 `PAID → CANCEL_REQUESTED → CANCELLED`, 실패 시 `PAID` 복귀(B6) | R1 |
| `claim` → `payment` | PG 취소 호출(멱등키 `cancel-{cancelRequestId}`). `CANCEL_REQUESTED`에 머문 주문의 복구(E48)도 claim이 payment를 호출한다 | R1 |
| `claim` → `inventory` | 취소 성공 시 재고 복구(정확히 한 번, 원장) | R1 |
| `payment` → `order` | 주문 생성, `current_attempt_id` 갱신, 조건부 전이(`PAYMENT_PENDING → CONFIRMING → PAID`, 실패 시 복귀), 웹훅·복구 작업자의 상태 확정 | R1 |
| `payment` → `inventory` | `CONFIRMING` 전이 안에서 재예약(B5-3-2), 승인 성공 시 예약 소진 | R1 |
| `shipping` → `order` | 조건부 전이 `PAID → PREPARING`(E45), `SHIPPED`·`DELIVERED` | R1 |
| `order` → `catalog` | 주문 시점에 가격·판매 상태를 원본으로 재검증(B0-2) | R1 |
| `order` → `inventory` | 재고 예약. 예약 만료 배치에서 주문 행을 잠근 뒤 예약 해제(B4, 잠금 순서는 ADR-005) | R1 |
| `order` → `cart` | 장바구니 항목 조회, 주문 후 비우기 | R1 |
| `order` → `member` | 주문자 확인 | R1 |
| `cart` → `catalog` | 상품·SKU 조회 | R1 |
| `order`·`claim` → `coupon` | 쿠폰 사용·복구, 안분 | R2b(예정) |
| `drop` → `inventory`·`order` | 래플·선착순 | R3(예정) |
| `settlement` → `order`·`payment` | 정산 원장 | R4(예정) |
| 모든 모듈 → `outbox` | 이벤트 발행 | R2a(예정, 이벤트 목록 ADR) |

- **결제 흐름은 payment가 조율한다.** B5-1(주문 생성 + 예약 + 시도 생성)도 payment 쪽 유스케이스가 `order`를 호출해 처리한다. order가 payment를 부르고 payment가 order를 부르는 순환을 없애기 위해서다. 유스케이스 이름과 시도 모델은 ADR-006a에서 정한다.
- **예약 만료 배치는 order가 조율한다.** "주문이 `CONFIRMING`이 아닐 때만 해제"하려면 주문 상태를 알아야 하는데, inventory가 order를 부르면 순환이 생긴다.
- `inventory`, `catalog`, `member`는 다른 모듈을 부르지 않는다.
- **이 ADR이 정하지 않는 것**: 공통 패키지(`Money`, 오류 형식, 인증 주체)의 위치와 범위는 R0-06과 ADR-008에서, 상태 이름과 소속은 ADR-004·006에서 정한다.

## 검토한 대안
| 대안 | 장점 | 기각 이유 |
|---|---|---|
| MSA | 모듈별 배포·확장 | 기획서 §9-4에서 하지 않기로 했다. 한 트랜잭션이 필요한 결제 흐름이 분산 트랜잭션이 된다 |
| 모듈 간 호출을 모두 이벤트로 | 결합이 느슨하다 | B5-1·B5-3-2는 한 트랜잭션이어야 한다. 추적과 디버깅 비용이 먼저 커진다(B0-1) |
| Spring Modulith | 모듈 검증과 문서 생성을 한 번에 한다. 2.1 계열이 Boot 4.1을 따라간다(spring.io 릴리스 발표, 2026-11-23 확인) | ADR-007·010의 코드 규칙(QueryDSL 범위, `@Setter` 금지, 돈 타입)에는 어차피 ArchUnit이 필요하다. 도구를 둘로 나누면 규칙이 두 곳에 흩어지고 의존성이 늘어난다 |
| order가 조율하고 payment는 포트 인터페이스로 결과를 돌려준다(의존 역전) | "주문 생성"이 order에 있어 이름이 자연스럽다 | 인터페이스와 구현이 모듈을 오가 흐름을 따라가기 어렵다. ArchUnit 규칙에 예외가 생긴다 |
| 순환을 허용하고 엔티티 접근만 막는다 | 규칙이 가장 단순하다 | 모듈을 나중에 떼어 낼 때 끊을 곳이 없다. 순환 금지는 ArchUnit 규칙 한 줄로 강제할 수 있다 |

## 결과
- **얻는 것**: 모듈 간 의존이 한 방향으로만 흐르고, 표 한 장으로 보인다. 결제 흐름의 트랜잭션 경계가 코드에서 한 곳(payment 유스케이스)에 모인다. 테스트와 배포가 앱 하나라 단순하다.
- **감수하는 것**: "주문 생성"을 payment 쪽 유스케이스가 조율해서 이름만 보면 어색하다. 모듈 간 트랜잭션 공유를 허용하므로 DB 단위로 모듈을 떼어 내려면 그 호출을 먼저 끊어야 한다. 표에 없는 호출이 필요해지면 ADR을 고쳐야 한다.

## 보안·개인정보 영향
- 모듈 경계는 보안 경계가 아니다. 같은 프로세스, 같은 DB다.
- 공개 API는 호출자가 고객인지 관리자인지 모른다. 그래서 소유자 검사(A5-9 S12)를 공개 API에 맡기지 않고, 고객 요청을 받는 쪽에서 한다. 어느 계층에서 하는지는 ADR-008에서 정한다.
- 관리자 API는 `admin` 모듈의 컨트롤러로만 연다. ArchUnit 규칙은 R0-06에서 만든다.

## 강제
- **예정(R0-06, ArchUnit)**: 다음 경우에 테스트가 실패한다.
  - 위 표에 없는 모듈 의존이 생겼다(예: `inventory`가 `order` 클래스를 import했다)
  - 모듈 사이에 순환이 있다
  - 다른 모듈의 공개 API 패키지가 아닌 클래스(엔티티·리포지토리 등)를 썼다
  - 관리자 컨트롤러가 `admin` 모듈 밖에 있다
- **리뷰**: 표에 없는 호출을 더하는 PR은 이 ADR을 대체하는 ADR을 함께 낸다.

---

## 재검토 조건
- 기능을 만들려면 표의 방향을 거스르는 호출이 필요해질 때: 의존 역전이나 이벤트(R2a Outbox)로 풀 수 있는지 먼저 보고, 안 되면 이 ADR을 대체한다.
- R2a에서 Outbox 이벤트 목록을 정할 때: Spring Modulith의 이벤트 발행 기록 기능도 비교한다.
- **분리 기준**: R3 부하 테스트에서 드롭 트래픽 때문에 주문·결제 경로가 R3에서 정한 목표를 넘고, 커넥션 풀 분리와 튜닝으로도 풀리지 않을 때만 `drop` 모듈의 분리를 검토한다. 그 밖의 모듈은 측정 근거 없이 떼지 않는다.

## 논의 기록
- AI 제안: ArchUnit 하나, payment가 결제 흐름 조율, 예약 만료는 order가 조율, 공개 API 패키지만 호출, 모듈 간 JPA 연관 금지.
- 사용자: 세 가지 결정 초안을 승인했다. 기획서 문구는 v0.3에서 정리하고 `.claude/rules/backend.md`는 이번에 고치기로 했다(2026-11-23).

## 문서 반영 체크
- [x] 기획서: 이번에는 고치지 않는다. §4-3·§9의 "ArchUnit 또는 Spring Modulith"는 기획서 v0.3에서 정리한다. 열린 선택지 중 하나를 고른 것이라 E3에 더할 것은 없다
- [x] 작업진행.md: A1·A4 표·A5-8의 "ArchUnit 또는 Modulith"를 "ArchUnit"으로 고쳤다
- [x] CLAUDE.md 또는 `.claude/rules/`: `.claude/rules/backend.md`의 "ArchUnit(또는 Modulith)"을 "ArchUnit"으로 고쳤다

## 보충 (작업진행.md A7 보충 규칙. 결정은 바꾸지 않고 표에 행을 더한다)
- **2026-11-24**(근거: ADR-005, R0 재검토 이슈 #39): 동기 호출 표에 다음 한 줄을 더한다. 순환은 생기지 않는다(`inventory`는 다른 모듈을 부르지 않는다).

| 호출하는 모듈 → 호출받는 모듈 | 용도 | 도입 |
|---|---|---|
| `shipping` → `inventory` | 출고(`SHIPPED`) 때 창고 재고(`on_hand`) 차감, 원장 기록 | R1 |

- 같은 날 다른 ADR이 기존 화살표에 용도를 더했다(새 화살표 아님): `payment → order`에 "결제창을 열 때 주문 만료 시각 갱신"(ADR-006a), `claim → payment`에 "고객 취소의 PG 결과 조회·재요청"(ADR-006b).
- **2026-11-24**(근거: ADR-006b, R0 재검토 독립 검토)
  - `claim → payment` 행의 "멱등키 `cancel-{cancelRequestId}`"는 ADR-006b의 `pg_cancel` 기록 ID(`cancel-{pg_cancel uid}`)로 읽는다. claim의 취소 요청 기록은 요청 시각·사유를 담고, PG 키와 결과는 payment의 `pg_cancel`이 담는다.
  - 같은 행의 "(E48)"은 "(E49)"의 잘못이다. E48은 `CONFIRMING` 직후 서버 종료, E49가 취소의 타임아웃·실패다.
  - 검토한 대안 표의 Spring Modulith 기각 이유 중 "`@Setter` 금지에 ArchUnit이 필요하다"는 틀렸다. `@Setter`는 컴파일 뒤에 남지 않아 `lombok.config`로 막는다(ADR-010 보충). QueryDSL 범위와 돈 타입 검사에는 여전히 ArchUnit이 필요하므로 결정은 그대로다.
- **2026-11-24**(근거: R0-06, 이슈 #53. 패키지 이름, 추가 검사, 공유 모듈은 2026-11-24 R0-06 최종 보고에서 사용자 승인. 공유 모듈은 "번거롭더라도 확실히 나누는 쪽"이라는 사용자 기준으로 `common` 하나를 대신한다)
  - **패키지**: 모듈은 `io.github.pinkcarver8_blip.commerce.<모듈>`, 공개 API는 `<모듈>.api`. R1 모듈 9개(catalog, inventory, cart, order, payment, shipping, claim, member, admin)만 만들었다. R2b 이후 모듈은 도입할 때 이 표를 확인하고 규칙의 허용 목록에 더한다. 업무 코드는 모두 `commerce` 아래에 둔다(밖에 두면 규칙을 피하는 우회로가 되므로 시험으로 막는다).
  - **공유 모듈**: 업무 모듈이 같이 쓰는 코드는 `common` 같은 공통 폴더 하나에 두지 않고, 이름과 할 일이 정해진 작은 공유 모듈 5개에 둔다. 공통 폴더는 모든 모듈이 부를 수 있어서, 업무 규칙(예: 배송비 계산)을 옮겨 놓으면 위 호출 표를 피하는 뒷문이 되기 때문이다.

| 공유 모듈 | 담는 것 | 부를 수 있는 쪽 | 이 모듈이 부르는 것 |
|---|---|---|---|
| `money` | 금액 값(`Money`), 소수 예외 표시(`AllowDecimal`) | 모든 업무 모듈 | 없음 |
| `id` | 외부 ID(UUIDv4) 생성 | 모든 업무 모듈 | 없음 |
| `error` | 오류 응답 형식과 오류 코드의 틀. 실제 코드 목록은 각 업무 모듈 | 모든 업무 모듈 | 없음 |
| `identity` | 로그인한 사람 정보(고객·관리자 주체, ADR-008 결정 6) | 모든 업무 모듈 | 없음 |
| `platform` | 앱을 돌리는 설정: 두 필터 체인(ADR-008), 전역 오류 처리기, 시간대·JSON 설정 | **없음** | `identity`, `error` |

  - **공유 모듈 규칙**(검사: `sharedModulesStayMinimal`·`sharedModulesMatchAllowlist`): ① DB 도구, `@Service`·`@Repository`·`@Transactional` 금지. ② `money`·`id`·`error`·`identity`는 Spring 부품(`@Component` 계열)이 아니고, 정적 함수는 자기 타입을 만드는 것만 둔다(`Money.of`는 되고 `Money.fee`는 안 된다). ③ 클래스와 공개 멤버는 `backend/src/test/resources/architecture/shared-modules-allowlist.txt`와 같아야 한다(`platform`은 클래스만). 이 목록을 바꾸는 일은 단위를 시작할 때 사용자 승인을 받는다(작업진행.md A3-8). 한 모듈에만 필요한 설정은 그 모듈 안에 두고, 루트 패키지에는 애플리케이션 시작 클래스만 둔다.
  - **공유 모듈이 못 막는 것**: 목록에 있는 함수의 속을 바꾸는 것(예: `Money.plus`가 수수료를 더하게 고침). `Money` 단위 시험과 코드 읽기로 막는다(`docs/known-issues.md` K11). 회원·관리자 모듈의 토큰 발급이 `platform`을 부르지 않고 되는지(Spring Security의 `JwtEncoder` 주입)는 R1-01에서 확인한다(작업진행.md E4-2).
  - **강제 칸의 "예정(R0-06)"을 구현했다**: `backend/src/test/java/io/github/pinkcarver8_blip/commerce/architecture/CommerceArchitectureRules.java`. 표에 없는 방향, 공개 API가 아닌 클래스 사용, 순환, 관리자 컨트롤러 위치가 걸린다. 더해서 ① 목록에 없는 모듈 패키지, ② 공개 API가 같은 모듈의 내부 타입(엔티티 등)을 매개변수·반환 타입으로 드러내는 것, ③ admin 밖에서 `/api/{변수}`·`/api/admi?`처럼 관리자 경로와 겹칠 수 있는 패턴을 여는 것도 실패로 잡는다. 관리자 컨트롤러의 클래스 경로는 보안 설정(ADR-008)과 같게 정확히 `/api/admin` 아래여야 한다(`/api/admins`는 실패). 상위 클래스·인터페이스에 단 경로와 상속한 타입·`throws`로 드러낸 내부 타입도 본다(독립 검토 지적 반영).
  - **못 잡는 것**: 리플렉션, 함수형 라우팅(`RouterFunction`)으로 연 경로, 컴파일할 때 값이 박히는 상수(`static final`)와 메서드 호출 없는 형 변환은 의존으로 보이지 않는다. 리뷰로 막는다(`docs/known-issues.md` K2·K3·K7).
