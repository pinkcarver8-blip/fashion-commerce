---
paths:
  - "backend/**/order/**"
  - "backend/**/payment/**"
  - "backend/**/claim/**"
  - "backend/**/shipping/**"
---

# [엄격] 상태 머신

원문: 작업진행.md A5-3, 계약은 B3(ADR-004·006a), 불변식 I6.

- 전이 규칙은 코드 한 곳에서만 정의한다. 허용되지 않은 전이는 거부한다.
- 종결 상태(EXPIRED, CANCELLED)는 어떤 경우에도 되살리지 않는다.
- 상태 전이 규칙표는 단위 테스트로 고정한다.
