# ADR-010. 공통 규약: 금액·시간·ID·오류·API·코드·빌드·공급망

- **상태**: 승인
- **날짜**: 2026-11-23
- **결정자**: 사용자 / **작성자**: Claude Code(Claude Opus 5.5)
- **관련**: 작업 ID R0-05-010(이슈 #15), 입력 R0-03([`docs/reports/r0-verification.md`](../reports/r0-verification.md) §6), 작업진행.md A1 "공통 규약"·"빌드·패키지" 행, A5-9, A5-12, A7

## 맥락
- 기획서는 공통 규약을 정하지 않았다. 작업진행.md A1이 규약을 정했고(E3 "공통 규약" 행), 이유와 강제 수단은 이 ADR에 두기로 했다.
- 코드를 쓰기 전(R0-06~08)에 정해야 한다. 금액 타입, 시간대, 외부 ID 형식은 DB 스키마와 API에 박혀서 나중에 바꾸기 어렵다.
- 우선순위는 보안 > 기획서 > 현업 표준 > 사용자가 직접 검토할 수 있는가 > 효율이다(`기획근거/7_작업진행근거.md` 라운드 9).
- 외부 ID는 토스 orderId 규칙을 따라야 한다: 6~64자, 영문 대소문자·숫자·`-`·`_`·`=`(R0-03에서 실제 결제로 확인).

## 결정
**아래 규약을 프로젝트 전체에 적용한다.** 세부 값은 작업진행.md A1에 있고, 이 표는 결정과 이유를 적는다.

| 규약 | 결정 | 이유 |
|---|---|---|
| 금액 | 원 단위 정수. Java `long` + `Money` 값 객체, DB `BIGINT`. 소수 타입 금지 | 원화에는 소수 단위가 없다. 부동소수점 오차를 원천 차단하고, 금액 연산을 `Money` 한 곳에 모은다 |
| 시간 | UTC 저장(`DATETIME(6)`, `Instant`). JVM·JDBC·Jackson·컨테이너 시간대를 UTC로 명시 고정. 영업일·환급 기한은 Asia/Seoul 날짜로 계산. API는 시간대가 붙은 ISO-8601 | 저장값이 서버 설정에 따라 바뀌지 않게 한다. 사람이 쓰는 "날짜"가 필요한 계산만 한국 날짜로 한다 |
| ID | 내부 PK는 `BIGINT` 자동 증가. 외부에 노출하는 주문번호와 결제 시도 ID(토스 orderId)는 **UUIDv4**(36자, `UUID.randomUUID()`) | 순차 PK는 인덱스에 유리하다. 외부 ID를 추측할 수 없게 해서 소유자 검사(A5-9 S12)의 2차 방어로 쓴다. UUIDv4는 16진수와 `-`뿐이라 토스 규칙을 만족한다. Java 문서는 `randomUUID()`가 "type 4" UUID를 "cryptographically strong pseudo random number generator"로 만든다고 적는다(Java 21 API, 2026-11-23 확인) |
| 오류 | RFC 9457 Problem Details + 정해 둔 목록의 `code`. 응답에 스택 트레이스·예외 메시지 금지. 인증 실패 메시지 일반화. 코드 목록은 API 명세서(A7)에 둔다 | 표준 형식이라 프론트가 한 가지로 처리한다. 내부 정보 노출을 막는다(A5-9 S9) |
| API | JSON camelCase, 목록 응답 형식 하나, URL 버전(`/v1`) 없음 | 소비자가 우리 프론트 두 개뿐이라 버전을 나눌 이유가 없다 |
| Java 코드 | DTO는 `record`. 엔티티에 `@Setter`·`@Data` 금지(필요한 변경 메서드만). 생성자 주입 | 상태 변경이 정해진 메서드로만 일어나게 한다(상태 머신·재고 불변식을 지키는 전제). 요청을 엔티티에 직접 바인딩하지 않는다(A5-9 S2) |
| 포매터 | Spotless + google-java-format(AOSP), Prettier + ESLint | 형식 논쟁과 리뷰 잡음을 도구로 없앤다 |
| 패키지명 | `io.github.pinkcarver8_blip.commerce` | GitHub 아이디(`pinkcarver8-blip`)의 `-`는 Java 식별자에 쓸 수 없다. JLS 21 §6.1은 도메인에 `-`가 있으면 `_`로 바꾸라고 권한다(2026-11-23 확인) |
| 빌드·패키지 도구 | 백엔드 Gradle **Groovy DSL**(wrapper). 프론트 **pnpm workspaces**(Turborepo·Nx 없음) | Groovy는 Spring Initializr 기본값이고 사용자의 이전 프로젝트가 모두 Groovy라 AI가 쓴 빌드 파일을 직접 검토할 수 있다. 프론트 앱은 두 개뿐이라 workspaces로 충분하다 |
| 공급망 | A5-12: 잠금 파일 커밋과 고정 설치, pnpm 기본 보호(공개 1일 미만 버전·설치 스크립트 차단)를 끄지 않는다, `packageManager` 필드로 pnpm 버전 고정, Gradle wrapper 검증, Actions SHA 고정, Dependabot, 새 의존성은 이유와 실재 확인 | 2025년 npm 웜(Shai-Hulud) 같은 공급망 공격을 기본 설정으로 막는다. AI가 지어낸 이름의 가짜 패키지를 막는다 |

- **이 ADR이 정하지 않는 것**: pnpm 메이저 버전(11 또는 12)은 R0-07, Gradle 의존성 서명 검증 도입 여부는 R0-08에서 정하고 작업진행.md A1·A5-12에 적는다. 원칙(버전 고정, 기본 보호 유지, 서명 검증은 시험 뒤 결정)은 여기서 정한다.

## 검토한 대안
| 대안 | 장점 | 기각 이유 |
|---|---|---|
| 외부 ID에 UUIDv7 | 시간순이라 인덱스에 유리하다 | 생성 시각이 드러난다. 외부 ID는 인덱스 성능보다 추측 불가가 목적이다(내부 PK가 순차다) |
| 주문번호를 사람이 읽기 쉬운 짧은 값으로 | 고객센터에서 불러 주기 쉽다 | 만들 코드가 하나 더 생긴다. 날짜를 앞에 붙이면 생성 시각이 드러난다 |
| 금액에 `BigDecimal` | 소수 통화와 비율 계산에 맞다 | 원화에는 소수가 없다. 객체 비교(`equals`와 scale) 실수가 생기기 쉽다 |
| KST로 저장 | 로그와 DB를 사람이 바로 읽는다 | 서버·DB 설정에 따라 값이 달라진다. 배포 환경이 바뀌면 깨진다 |
| Gradle Kotlin DSL | 타입 검사와 IDE 자동 완성 | 보안 면에서는 같다. 사용자가 직접 검토하기 어렵다 |
| Turborepo·Nx | 빌드 캐시, 작업 순서 관리 | 앱 두 개에는 과하다. 도구가 하나 더 늘어 공급망 표면도 늘어난다 |

## 결과
- **얻는 것**: 금액·시간·ID 실수를 타입과 설정으로 막는다. 외부 ID로 다른 사람의 주문을 추측할 수 없다. 오류 응답이 한 형식이다. 공급망 기본 방어가 켜진 채로 시작한다.
- **감수하는 것**: UUIDv4 외부 ID는 길고(36자) 사람이 읽기 어렵다. 인덱스에 불리하지만 외부 ID 컬럼에만 해당한다. 시간대를 네 곳(JVM·JDBC·Jackson·컨테이너)에 따로 고정해야 한다. 고정 설치와 SHA 고정 때문에 의존성 업데이트가 PR을 거쳐야 한다.

## 보안·개인정보 영향
- **외부 ID**: 추측 불가 ID는 2차 방어다. 1차 방어는 소유자 검사(A5-9 S12)이고, 이 ADR은 그것을 대신하지 않는다.
- **오류 응답**: 스택 트레이스·예외 메시지를 싣지 않고 인증 실패를 일반화해 내부 정보와 계정 존재 여부를 숨긴다(S9).
- **공급망**: 고정 설치, pnpm 기본 보호, wrapper 검증, Actions SHA 고정으로 의존성·빌드 경로의 변조를 막는다(A5-12).
- 개인정보 자체를 다루는 규약은 없다. 로그 마스킹은 A1 "운영" 행을 따른다.

## 강제
- **예정(R0-06, ArchUnit)**: 엔티티에 `@Setter`·`@Data`를 쓰면 실패한다. 돈 필드를 `double`·`float`·`BigDecimal`로 선언하면 실패한다(돈 필드를 가려내는 방법은 R0-06에서 정한다).
- **예정(R0-08, CI)**: Spotless·Prettier·ESLint 검사. 시간대 고정 확인 테스트(JVM·JDBC·Jackson 중 하나라도 UTC가 아니면 실패). 공급망 검사(고정 설치, wrapper 검증, Actions SHA 고정).
- **예정(R1)**: ID 생성기 단위 테스트(값이 토스 orderId 규칙을 만족하고 UUID 버전이 4인지). 오류 응답 테스트(스택 트레이스·예외 메시지가 없는지).
- **리뷰**: 목록 응답 형식, camelCase, 오류 `code`가 목록에 있는지는 PR에서 확인한다.

---

## 재검토 조건
- 고객센터 화면(관리자)에서 주문번호를 불러 주기 어렵다는 문제가 실제로 생길 때: 주문번호만 짧은 무작위 값으로 바꾸는 것을 검토한다.
- 외국 통화(소수 단위)를 다루게 될 때: 금액 타입을 다시 본다.
- 외부 소비자(제3자 API)가 생길 때: URL 버전을 다시 본다.

## 논의 기록
- AI 제안: 외부 ID는 주문번호·결제 시도 ID 모두 UUIDv4. 공급망 버전 결정은 원칙만 ADR에 두고 숫자는 R0-07·08에서 A1·A5-12에. 패키지명은 JLS 관례대로 `-`를 `_`로.
- 사용자: 세 가지 모두 승인했다(2026-11-23).

## 문서 반영 체크
- [x] 기획서: 바꿀 것이 없다(공통 규약은 기획서에 없고 E3에 있다)
- [x] 작업진행.md: A1 패키지명을 확정값으로 고치고 ID 항목에 UUIDv4를 적었다. E4-2의 R0-03 행(orderId 규칙)과 R0-04 행(ADR-007에 반영 완료)을 지웠다. R0-07(pnpm)·R0-08(서명 검증) 행의 "반영할 곳"에서 ADR-010을 뺐다
- [x] CLAUDE.md 또는 `.claude/rules/`: 바꿀 것이 없다

## 보충 (작업진행.md A7 보충 규칙. 결정은 바꾸지 않고 강제 수단과 근거를 정정한다)
- **2026-11-24**(근거: R0 재검토 이슈 #39, 독립 검토)
  - **`@Setter`·`@Data` 금지의 강제 수단 정정**: "강제" 칸의 "엔티티에 `@Setter`·`@Data`를 쓰면 ArchUnit이 실패한다"는 동작하지 않는다. 두 표시는 `@Retention(RetentionPolicy.SOURCE)`라 컴파일된 클래스에 남지 않고, ArchUnit은 컴파일된 클래스를 읽기 때문이다(Lombok 저장소의 `Setter.java`·`Data.java`, 2026-11-24 확인). 대신 `lombok.config`에 `lombok.setter.flagUsage = error`와 `lombok.data.flagUsage = error`를 두어 컴파일을 실패시킨다(Lombok 공식 문서의 설정 키, 2026-11-24 확인). R0-06에서 실제로 컴파일이 실패하는지 시험한다. 돈 필드 타입 검사는 그대로 ArchUnit으로 한다.
  - **시간대 고정 검사 범위**: "강제" 칸의 테스트는 JVM·JDBC·Jackson 세 곳만 본다. 네 번째인 컨테이너 시간대(`TZ`)는 R0-08 CI의 컨테이너 기동 확인에서 검사한다.
  - **근거가 없는 문장 두 개**(→ 2026-11-24 R0-07에서 확인, 아래 보충): "pnpm 11의 기본 보호(공개 1일 미만 버전 설치 금지, 설치 스크립트 차단)"와 "Groovy는 Spring Initializr 기본값"은 출처와 확인 날짜를 적지 않았다(미확인). R0-07에서 pnpm 공식 문서와 Initializr로 확인한다(작업진행.md E4-2). 확인 결과가 다르면 이 보충에 적고, 결정에 영향이 있으면 새 ADR로 대체한다.
- **2026-11-24**(근거: 사용자 결정, 이슈 #51): **Lombok을 쓴다.** 이 ADR의 "엔티티에 `@Setter`·`@Data` 금지" 결정은 그대로이고, 그 금지를 지키는 방법을 정한다.
  - `lombok.config`: `lombok.setter.flagUsage = error`, `lombok.data.flagUsage = error`(컴파일 실패). R0-06에서 실제로 실패하는지 시험한다.
  - JPA 엔티티에는 `@EqualsAndHashCode`·`@ToString`도 쓰지 않는다. 지연 로딩·연관관계 때문에 버그가 나기 쉽다(Thorben Janssen, "Lombok & Hibernate: How to Avoid Common Pitfalls", 2026-11-24 확인). 이 두 어노테이션은 `lombok.config`로 전체 금지하면 DTO가 아닌 일반 클래스에도 막히므로, 엔티티(`@Entity`)에서만 ArchUnit으로 막을 수 있는지 R0-06에서 확인한다. 소스에만 남는 어노테이션이라 ArchUnit으로 못 잡으면 리뷰로 막는다.
  - 공급망 대책은 작업진행.md A5-12의 "Lombok 대책".
  - 확인한 것(2026-11-24): Lombok에 붙은 CVE는 찾지 못했다(검색 결과가 오래돼 최신은 R0-08의 Dependabot으로 본다). Lombok 공식 보안 정책은 "컴파일할 때만 동작하고 서버·배포물에는 필요 없다"고 적는다.
- **2026-11-24**(근거: R0-06, 이슈 #53, 사용자 결정 "돈 필드 규칙은 추천대로". 필드 밖(매개변수·반환·호출)까지 넓힌 범위는 2026-11-24 R0-06 최종 보고에서 사용자 승인)
  - **돈 필드를 가려내는 방법**: 가려내지 않는다. 백엔드 코드 전체에서 `double`·`float`·`Double`·`Float`·`BigDecimal`과 double을 다루는 타입(`OptionalDouble`, `DoubleStream`, `DoubleAdder`, `java.util.function`의 `Double…`·`ToDouble…` 등)을 필드·매개변수·반환 타입·호출(예: `Math.round(double)`, `BigDecimal.valueOf`)에 쓰면 실패한다. 예외는 `money.AllowDecimal(reason)`(공유 모듈 `money`, ADR-001 보충) 표시를 단 멤버뿐이다. 표시를 단 필드도 표시 없는 메서드는 읽고 쓸 수 없고, 그 클래스의 생성자만 자기 필드를 채울 수 있다(record 생성자 때문). 다른 클래스가 받아 쓰려면 그쪽 메서드에도 표시가 필요하다(독립 검토 지적 반영). 엔티티 필드에 단 표시는 빌드가 만드는 Q클래스까지 따라가지 않으므로 Q클래스는 검사하지 않는다(엔티티에서 이미 검사한다). 못 잡는 것: 메서드 호출 없이 계산식 안에서만 쓰는 소수(예: `(long) (price * 0.9)`). 리뷰로 막는다(`docs/known-issues.md`).
  - **`lombok.config` 시험 결과**: `@Setter`·`@Data`를 쓰면 컴파일이 실패하고 `@Getter`는 통과한다(`LombokConfigTest`, 실제 `lombok.config`로 컴파일). 설정 줄을 지우면 이 시험이 실패하는 것도 확인했다(역검사). `config.stopBubbling = true`를 두고, `src` 아래에 다른 `lombok.config`가 있으면 실패한다.
  - **엔티티 `@EqualsAndHashCode`·`@ToString`은 ArchUnit으로 잡힌다**: 두 표시는 컴파일 뒤에 남지 않지만, Lombok이 만든 메서드에 붙이는 `@lombok.Generated`는 클래스 파일에 남는다(1.18.48에서 바이트코드로 확인). `@Entity`·`@MappedSuperclass`의 `equals`·`hashCode`·`toString`에 이 표시가 있으면 실패한다. 1.18.48은 설정이 없어도 이 표시를 붙이지만, `lombok.addLombokGeneratedAnnotation = true`를 명시했고 `false`로 바꾸면 시험이 실패한다(역검사). 손으로 쓴 ID 기준 `equals`는 허용한다.
  - **Lombok 버전**: 1.18.48(2026-09-01 공개, Spring Boot 4.1.3이 관리하는 버전). 잠금 파일에 고정했고, 실행 클래스패스와 bootJar에 들어가지 않는 것을 확인했다.
- **2026-11-24**(근거: R0-07, 이슈 #56): 위 "근거가 없는 문장 두 개"를 확인했다. 결정은 그대로다.
  - pnpm 기본 보호: 공개 1일(1440분) 미만 판 설치 금지, 의존성 설치 스크립트 차단, 이상한 경로 하위 패키지 차단이 기본으로 켜져 있다. 11과 12가 같다(pnpm 공식 문서 pnpm.io/settings·supply-chain-security, 11.0 릴리스 노트, 2026-11-24 확인).
  - Spring Initializr 기본 빌드 형식은 `gradle-project`(Groovy DSL)다(start.spring.io 메타데이터, 2026-11-24 확인).
  - pnpm은 사용자 결정으로 12를 쓴다(작업진행.md A1·A5-12). 캐시는 Redis 8이다(A1).
