# ADR-007. QueryDSL: OpenFeign 포크를 읽기 조회에만 쓴다

- **상태**: 승인
- **날짜**: 2026-11-22
- **결정자**: 사용자 / **작성자**: Claude Code(Claude Opus 5.5)
- **관련**: 작업 ID R0-05-007(이슈 #11), 입력 R0-04(이슈 #7, [`docs/reports/r0-querydsl.md`](../reports/r0-querydsl.md)), 기획서 §9-2 "QueryDSL 주의", 작업진행.md A1·A5-2·A5-8·A5-12

## 맥락
- 상품 목록(PLP) 필터, 관리자 검색, 정산 조회는 조건이 있을 수도 없을 수도 있는 **동적 조회**다. 조건 조합마다 쿼리를 따로 쓰거나 문자열을 이어 붙이면 실수가 생기고 컴파일러가 잡지 못한다.
- 한국 커머스 공고에 "JPA/QueryDSL"이 직접 나온다(기획서 §9-2).
- 그러나 Spring Data JPA 공식 문서는 원본 QueryDSL의 유지보수가 느려져 커뮤니티가 OpenFeign 아래로 포크했고, Spring Data는 포크를 "best-effort"로 지원한다고 적는다(기획서 §9-2, 2026-11-07 확인). 그래서 A1은 "R0에서 빌드를 확인하고, 안 되면 Specification·JPQL로 대체"로 정해 두었다.
- R0-04 결과(2026-11-21): 포크 7.7이 Java 21 + Spring Boot 4.1.3 + Hibernate 7.4.15 + MySQL 8.4.13에서 빌드되고, 동적 조건·정렬·페이지·조인·DTO 조회·Spring Data 저장소 결합이 모두 동작했다.

## 결정
**OpenFeign 포크 QueryDSL(`io.github.openfeign.querydsl`, 7.7)을 쓰되, 읽기 조회에만 쓰고 조회 전용 클래스 안에 가둔다.**
- **쓰는 곳**: 동적 조건이 있는 읽기 조회(PLP 필터, 관리자 검색, 정산 조회). 결과는 DTO(`record`)로 바로 받는다(`Projections.constructor`).
- **쓰지 않는 곳**: 돈·재고·상태를 바꾸는 쓰기. 이런 쓰기는 A5-2의 조건부 원자 UPDATE(`qty >= n`, 영향 행 수 확인)를 Spring Data 쿼리(`@Modifying` JPQL)로 쓴다. 쓰기 경로를 한 가지로 두어 검토와 테스트 대상을 좁힌다.
- **두는 곳**: QueryDSL 코드는 조회 전용 클래스(Spring Data 저장소에 붙이는 `…RepositoryImpl` 등. 이름·패키지 규칙은 R0-06)에만 둔다. 포크가 멈추면 이 클래스만 Specification·JPQL로 바꾼다.
- **설정**(R0-04 보고서 §3): 포크는 Boot의 버전 관리 목록에 없으므로 버전을 직접 고정한다. 주석 처리기는 `querydsl-apt:7.7:jpa`. Q클래스는 빌드할 때 만들어지며 git에 넣지 않는다.

## 검토한 대안
| 대안 | 장점 | 기각 이유 |
|---|---|---|
| 원본 QueryDSL(`com.querydsl` 5.1.0. Boot 4.1.3의 관리 목록에 있다) | 버전을 Boot가 관리한다 | Spring Data 문서가 유지보수가 느려졌다고 적는다. 이번에 시험하지 않았다 |
| Spring Data Specification | 의존성이 더 없다. Spring Data가 직접 지원한다 | 조인·DTO 조회·개수 조회를 쓰기 번거롭고 코드가 길다. **포크가 멈출 때의 대체안**으로 둔다 |
| JPQL·네이티브 SQL 문자열 | 가장 단순하고 SQL이 그대로 보인다 | 동적 조건을 문자열로 이어 붙여야 하고 타입 검사가 없다. 고정된 쿼리(쓰기의 조건부 UPDATE)에는 이 방식을 쓴다 |
| jOOQ | DB 스키마에서 타입 안전한 SQL을 만든다 | JPA와 별도로 두 번째 데이터 접근 방식이 생긴다. 공고 키워드(JPA/QueryDSL)와도 맞지 않는다 |

## 결과
- **얻는 것**: 동적 조회를 컴파일러가 검사하는 Java 코드로 쓴다(필드 이름이 틀리면 빌드가 실패한다). 빈 조건은 `where`에서 빠지므로 조건 조합마다 쿼리를 따로 쓰지 않는다. DTO로 바로 받아 지연 로딩 문제(N+1)를 피한다. 첫 페이지가 덜 차면 개수 조회를 생략한다(R0-04 보고서 §4).
- **감수하는 것**: 커뮤니티 포크에 기댄다. Boot·Hibernate를 올릴 때마다 호환을 다시 확인해야 한다. 버전을 직접 관리한다. 빌드에 주석 처리기 단계가 하나 더 생긴다.

## 보안·개인정보 영향
- 값은 바인딩 파라미터(`?`)로 들어간다(R0-04에서 실행된 SQL로 확인). 따라서 문자열 이어 붙이기보다 SQL 주입 위험이 낮다.
- 남는 위험이 두 가지 있다. 첫째, `Expressions.stringTemplate` 같은 템플릿 함수에 사용자 입력을 넣으면 주입이 가능하다. 그래서 사용자 입력은 템플릿에 넣지 않는다. 둘째, 정렬 필드를 요청값으로 받을 때 임의의 속성 이름을 그대로 쓰지 않고 허용 목록으로 매핑한다.
- 개인정보에는 영향이 없다(조회 방식만 정한다).

## 강제
- **예정(R0-06, ArchUnit)**: QueryDSL(`com.querydsl..`)에 의존하는 클래스는 조회 전용 클래스뿐이다(빌드가 만드는 Q클래스는 뺀다). `JPAUpdateClause`·`JPADeleteClause`·`JPAInsertClause`와 `JPAQueryFactory`의 `update`·`delete`·`insert` 호출은 아무 클래스도 쓰지 않는다. 확인 방법은 이렇다. 조회 전용이 아닌 클래스가 `JPAQueryFactory`를 쓰면 테스트가 실패하고, `JPAUpdateClause`를 쓰면 테스트가 실패한다.
- **예정(R0-08, CI)**: 빌드에서 Q클래스가 생성되지 않으면 컴파일이 실패한다. 따로 검사를 두지 않는다.
- **리뷰**: 템플릿 함수에 사용자 입력이 들어가는지, 정렬 허용 목록을 쓰는지는 PR에서 확인한다.

---

## 재검토 조건
- 포크가 12개월 동안 새 판을 내지 않을 때(7.7은 2026-09-23 공개)
- Boot·Hibernate를 올렸는데 빌드나 R0-04 시나리오 테스트가 깨질 때(예: Boot 4.2. 2026-11-19 공개)
- 부하 테스트(기획서 §11)에서 QueryDSL이 만든 SQL이 병목으로 나올 때

## 논의 기록
- AI 제안: 포크를 쓰되 읽기 조회로 범위를 좁히고, 쓰기는 `@Modifying` JPQL로 둔다. 강제는 ArchUnit으로 한다.
- 사용자: 이슈 초안의 결정 초안을 승인했다(2026-11-22).

## 문서 반영 체크
- [x] 기획서: 바꿀 것이 없다(§9-2의 "빌드를 먼저 확인한다"를 따랐다)
- [x] 작업진행.md E3: 바꿀 것이 없다(A1의 QueryDSL 행을 그대로 확정했다)
- [x] CLAUDE.md 또는 `.claude/rules/`: 바꿀 것이 없다. 규칙은 R0-06에서 ArchUnit으로 강제한다

## 보충 (작업진행.md A7 보충 규칙. 결정은 바꾸지 않고 다른 ADR과의 관계를 밝힌다)
- **2026-11-24**(근거: ADR-004·005, R0 재검토 이슈 #39): "쓰지 않는 곳"의 "돈·재고·상태를 바꾸는 쓰기는 조건부 원자 UPDATE(`@Modifying` JPQL)" 문장은 이렇게 읽는다.
  - 재고 수량과 예약 기록처럼 **행 하나를 조건으로 바꾸는 쓰기**: 조건부 원자 UPDATE(ADR-005).
  - **주문·결제 시도의 상태 전이**: 주문 행을 잠근 뒤 엔티티의 도메인 메서드로 바꾼다(ADR-004·006a). 같은 트랜잭션에서 다른 행(재예약 등)도 바꿔야 하기 때문이다.
  - 어느 쪽도 QueryDSL을 쓰지 않는다는 이 ADR의 결정은 그대로다.
- **2026-11-24**(근거: R0-06, 이슈 #53. 패키지 이름은 "제안"이고 R0-06 최종 보고에서 승인받는다): 조회 전용 클래스는 `<모듈>.query` 패키지에 둔다. 강제 칸의 "예정(R0-06)"을 구현했다(`backend/src/test/java/io/github/pinkcarver8_blip/commerce/architecture/CommerceArchitectureRules.java`의 `queryDslOnlyInQueryPackages`·`noQueryDslWrites`). 빌드가 만드는 Q클래스는 "이름이 Q + X이고 QueryDSL `Path`를 구현하며, 같은 패키지에 JPA 표시(`@Entity`·`@Embeddable`·`@MappedSuperclass`)가 붙은 X가 있는 클래스"로 가려 뺀다. 이름만 Q로 지은 손 클래스는 빠지지 않는다(독립 검토 지적 반영). 쓰기는 `DMLClause` 계열 사용과 `JPQLQueryFactory`의 `update`·`delete`·`insert` 호출을 모두 막는다(`query` 패키지 안에서도).
