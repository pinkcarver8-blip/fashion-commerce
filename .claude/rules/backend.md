---
paths:
  - "backend/**"
---

# 백엔드 공통 규칙

원문: 작업진행.md A1(공통 규약 행), A5-8, B1. 상세는 ADR-001·ADR-010.

- 모듈러 모놀리스다. 모듈 목록은 B1, 모듈 간 동기 호출은 ADR-001의 목록에 있는 것만 쓴다. 의존 규칙은 ArchUnit이 CI에서 막는다(ADR-001).
- 패키지(R0-06): 다른 모듈은 `<모듈>.api`만 쓴다. QueryDSL은 `<모듈>.query`에서 읽기 조회로만 쓴다. 공통 코드는 `common`(어떤 모듈에도 의존하지 않는다). 새 모듈 패키지는 B1·ADR-001과 `CommerceArchitectureRules.ALLOWED_CALLS`에 먼저 더한다.
- 금액은 원 단위 정수(`long` + `Money`, DB `BIGINT`). 소수 타입(`double`·`float`·`BigDecimal`)은 돈이 아니어도 금지하고, 꼭 필요하면 `@AllowDecimal(reason)`을 달아 리뷰받는다(ADR-010 보충).
- 시간은 UTC 저장(`Instant`, `DATETIME(6)`). 영업일·환급 기한만 Asia/Seoul 날짜로 계산한다.
- 외부에 노출하는 ID(주문번호, 결제 시도 ID)는 추측할 수 없는 무작위 값. 순차 PK와 UUIDv7은 내보내지 않는다.
- 오류는 RFC 9457 + 정해 둔 `code`. 응답에 스택 트레이스·예외 메시지·SQL을 싣지 않는다.
- DTO는 `record`. `@Setter`·`@Data`는 `lombok.config`가 컴파일에서 막는다. 엔티티에 Lombok `@EqualsAndHashCode`·`@ToString` 금지(ArchUnit). 필요한 변경 메서드만 둔다. 생성자 주입. `lombok.config`를 하위 폴더에 더 두지 않는다.
- 외부 호출(PG, 택배)은 트랜잭션 밖에서 한다.
- Redis는 캐시·대기열·레이트리밋 전용이다. 재고의 원천이 아니다.
- 인증·인가·CORS 설정을 고칠 때는 파일 이름과 상관없이 `.claude/rules/security.md`와 작업진행.md A5-9를 먼저 읽는다.
