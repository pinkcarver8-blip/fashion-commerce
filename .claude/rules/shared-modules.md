---
paths:
  - "backend/**/commerce/money/**"
  - "backend/**/commerce/id/**"
  - "backend/**/commerce/error/**"
  - "backend/**/commerce/identity/**"
  - "backend/**/commerce/platform/**"
  - "backend/**/shared-modules-allowlist.txt"
---

# [엄격] 공유 모듈

원문: ADR-001 보충 "공유 모듈", 작업진행.md A3-8.

- 두 모듈이 같이 쓰는 **업무 규칙**(배송비, 할인, 재고 판단 등)은 공유 모듈로 옮기지 않는다. 그 규칙의 주인 모듈이 `<모듈>.api`로 연다.
- 공유 모듈에는 값(record·enum·인터페이스·표시)과 앱 설정(`platform`)만 둔다. DB 도구, `@Service`·`@Repository`·`@Transactional`은 쓰지 않는다.
- `money`·`id`·`error`·`identity`의 정적 함수는 자기 타입을 만드는 것만 둔다. 아무 모듈도 `platform`을 부르지 않는다.
- 클래스나 공개 멤버를 더하거나 지우려면 `shared-modules-allowlist.txt`를 고쳐야 하고, 이 목록 변경은 **단위를 시작할 때 사용자 승인**을 받는다. 단위 중간에 필요해지면 멈추고 묻는다.
