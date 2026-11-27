# R0 QueryDSL 스모크 빌드 결과 (R0-04)

- 이슈: #7. ADR-007(QueryDSL을 쓸지, Specification·JPQL로 대체할지)의 입력이다(작업진행.md A1, E4-2).
- 확인 방법: 시험 프로젝트 `tools/querydsl-smoke/`를 만들고 `./gradlew clean build`(컴파일 + Q클래스 생성 + 테스트)를 돌렸다. 테스트는 Testcontainers로 띄운 실제 MySQL에서 실행했다.
- 확인 날짜: 2026-11-21.
- 시험 프로젝트의 패키지명(`io.github.pinkcarver8blip`)은 ADR-010의 규약(`io.github.pinkcarver8_blip.commerce`)보다 먼저 만들었다. 버리는 시험 코드라 고치지 않는다. 실제 백엔드(R0-07)는 ADR-010을 따른다.

## 1. 결론

**OpenFeign 포크 QueryDSL 7.7은 Java 21 + Spring Boot 4.1.3 + Hibernate 7.4 + MySQL 8.4에서 빌드되고 동작한다.** 이슈의 테스트 시나리오 5개가 모두 통과했다. 대체안(Specification·JPQL)으로 바꿀 이유가 없어 대체 구현은 만들지 않았다(수용 기준: 안 되는 부분이 있을 때만).

## 2. 버전

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | Microsoft OpenJDK 21.0.12 | Gradle 툴체인 21 |
| Gradle | 9.9.1(wrapper) | toss-probe와 같은 wrapper. 9.9.2가 2026-11-19에 나왔다 |
| Spring Boot | 4.1.3 | 4.1 최신 패치(2026-11-19 공개). 4.2.0도 같은 날 나왔으나 A1이 4.1.x로 정했다 |
| Spring Data JPA | 4.1.3 | Boot 관리 |
| Hibernate ORM | 7.4.15.Final | Boot 관리 |
| QueryDSL | `io.github.openfeign.querydsl` 7.7(2026-09-23 공개) | `querydsl-jpa` + 주석 처리기 `querydsl-apt:7.7:jpa` |
| MySQL | 8.4.13(이미지 `mysql:8.4`) | 드라이버 `mysql-connector-j` 26.10.0(Boot 관리) |
| Testcontainers | 2.0.5 | Boot 관리. 모듈 이름이 `testcontainers-mysql`, 클래스는 `org.testcontainers.mysql.MySQLContainer`(1.x와 다르다) |

새 의존성은 모두 Maven Central에 실제로 있는 것을 확인했다(A5-12).

## 3. 설정에서 주의할 점 (R0-07 백엔드에 옮겼다, 2026-11-24)

- **버전을 직접 적는다.** Boot가 관리하는 `querydsl.version`(5.1.0)은 원본(`com.querydsl`) 것이라 포크(`io.github.openfeign.querydsl`)에는 적용되지 않는다. 포크는 Boot 관리 목록에 없으므로 버전을 의존성에 직접 적는다.
- **주석 처리기는 `jpa` 분류자를 쓴다.** 포크 문서(openfeign.github.io/querydsl/tutorials/jpa)의 설정이다. `jakarta` 분류자도 있지만 문서 기준을 따랐다. `jakarta.persistence-api`도 주석 처리기 경로에 넣는다(버전은 Boot 관리).
  ```groovy
  implementation "io.github.openfeign.querydsl:querydsl-jpa:7.7"
  annotationProcessor "io.github.openfeign.querydsl:querydsl-apt:7.7:jpa"
  annotationProcessor 'jakarta.persistence:jakarta.persistence-api'
  ```
- **Q클래스는 빌드할 때 만들어진다.** 위치는 `build/generated/sources/annotationProcessor/java/main/`이고 git에 넣지 않는다(`build/`는 제외 대상). 따로 설정할 것이 없다.
- **JDK 23 이상으로 올리면** 주석 처리기가 기본으로 꺼진다(`-proc:full` 필요. 포크 문서). Java 21에서는 해당 없다(ADR-002).
- **Testcontainers 컨테이너는 Spring 빈(`@Bean @ServiceConnection`)으로 둔다.** 처음에 JUnit 확장(`@Testcontainers` + `@Container` 정적 필드)으로 띄웠더니, 컨테이너가 Spring 컨텍스트보다 먼저 멈춰 종료할 때 테이블 삭제(`create-drop`)가 연결을 30초 기다리다 실패했다(테스트 결과는 통과). 빈으로 바꾸자 사라졌다.

## 4. 확인한 조회와 실제 SQL

조회 코드는 `ProductSearchRepositoryImpl`, 테스트는 `ProductSearchRepositoryTest`다. 상품 3개(카테고리 2개, 1만·3만·5만 원)로 시작한다.

| 시나리오 | 결과 | 실행된 SQL(요약) |
|---|---|---|
| 카테고리 조건만 | 그 카테고리 상품만, 가격 순 | `… join category c1_0 on … where c1_0.id=? order by p1_0.price,p1_0.id limit ?,?` |
| 가격 2만~6만 원만 | 2건 | `… where p1_0.price>=? and p1_0.price<=? order by … limit ?,?` |
| 조건 모두 비움 | 3건 | `where` 절 자체가 없다 |
| 가격 오름차순, 페이지 크기 2 | 첫 페이지 2건, 전체 3건, 2페이지 | 위 조회 + `select count(p1_0.id) from product p1_0` |
| 카테고리 조인 + DTO | `categoryName`이 채워짐 | `select p1_0.id,p1_0.name,p1_0.price,c1_0.name from product p1_0 join category c1_0 …` |

- **동적 조건**: 값이 null인 조건은 `null`을 돌려주고, `where(...)`가 null을 건너뛴다. 조건 조합마다 쿼리를 따로 짜지 않아도 된다(PLP 필터·관리자 검색에 쓸 방식).
- **DTO 바로 받기**: `Projections.constructor`로 `record`를 채웠다. 엔티티를 거치지 않으므로 지연 로딩 문제(N+1)가 생기지 않는다.
- **개수 조회 생략**: `PageableExecutionUtils.getPage`를 쓰면 첫 페이지가 덜 찼을 때 개수 조회를 하지 않는다. 실제로 개수 조회는 페이지 크기 2인 시나리오에서만 한 번 실행됐다.
- **Spring Data와 함께 쓰기**: `ProductRepository extends JpaRepository<…>, ProductSearchRepository`로 묶고, 구현은 이름 규칙(`ProductSearchRepositoryImpl`)으로 Spring Data가 찾아 붙였다. 별도 설정은 `JPAQueryFactory` 빈 하나뿐이다.

## 5. 빌드 경고

| 경고 | 원인 | 조치 |
|---|---|---|
| `Cannot find annotation method 'from()' in type 'Range': class file for org.jetbrains.annotations.Range not found`(26건) | querydsl-core·jpa 7.7 클래스에 붙은 `org.jetbrains.annotations.Range`가 우리 컴파일 경로에 없다 | 동작 영향 없음. `-Xlint:all`을 켜 둔 시험 프로젝트에서 보였다. R0-07에서 경고가 거슬리면 `compileOnly 'org.jetbrains:annotations'`를 검토한다 |
| `No processor claimed any of these annotations` | `-Xlint:all`의 processing 경고. 처리기가 맡지 않은 주석(@Bean 등)을 알린다 | 무시해도 된다 |
| `Sharing is only supported for boot loader classes…`(테스트 JVM) | 테스트 JVM의 부트 클래스 경로에 jar가 더해질 때 나는 JVM 안내(클래스 공유 기능을 일부만 쓴다) | 무시해도 된다 |

## 6. ADR-007에 넘길 것

- **결정 제안**: QueryDSL(OpenFeign 포크)을 쓴다. 범위는 기획서 §9-2 "QueryDSL 주의"대로 읽기 조회(PLP 필터, 관리자 검색, 정산 조회)이고, 돈·재고를 바꾸는 쓰기에는 쓰지 않는다.
- **위험**: 포크는 커뮤니티 유지보수다. 원본(`com.querydsl`)은 Boot 관리 목록에 5.1.0이 남아 있지만, 이번에 확인한 것은 포크 7.7 + Hibernate 7.4 조합뿐이다. 포크가 멈추면 Specification·JPQL로 옮긴다. 옮길 부담을 줄이려면 QueryDSL 코드를 `…RepositoryImpl` 같은 조회 전용 클래스에만 둔다.
- **미결**: Gradle 잠금 파일(A5-12)은 시험 프로젝트에 넣지 않았다. 실제 백엔드(R0-07)에서 넣을지 정한다. → R0-06에서 넣었다(`backend/gradle.lockfile`, 엄격 모드).

## 7. Spring Boot 4.2.0 재확인 (2026-11-27, 이슈 #78)

- 바꾼 것: 시험 프로젝트의 Spring Boot 4.1.3 → 4.2.0(Spring Data 2026.1.0, Spring Framework 7.1.0). Hibernate는 7.4.15 그대로, 포크 7.7 그대로. 시험 프로젝트의 Gradle은 9.9.1 그대로.
- 결과: `./gradlew clean build` 통과(2분 37초), §4의 시나리오 5개 모두 통과. 경고는 §5와 같다.
