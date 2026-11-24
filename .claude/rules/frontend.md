---
paths:
  - "frontend/**"
---

# 프론트엔드 공통 규칙

원문: 작업진행.md A2, A5-9(S4·S5), A8, C. 상세는 ADR-009.

- `store`는 `admin` 쪽 패키지와 관리자용 API 클라이언트를 import하지 않는다(린트로 막는다).
- 금액은 서버가 준 값을 표시만 한다. 화면에서 금액을 계산해 서버에 보내지 않는다.
- `dangerouslySetInnerHTML`을 쓰지 않는다(린트로 막는다).
- 토큰은 메모리에만 둔다. localStorage·sessionStorage에 두지 않는다(린트 오류). 여러 탭의 토큰 갱신은 Web Locks로 한 번에 한 탭만 한다(ADR-008).
- `packages/ui`는 `api-client`를 import하지 않는다. 앱 → 패키지 한 방향만(ADR-009).
- `VITE_`로 시작하는 환경변수는 브라우저에 공개된다. 시크릿 키를 넣지 않는다(토스는 클라이언트 키만).
- 색·간격·글꼴은 `tokens.css`의 토큰만 쓴다(A8).
- 결제·재고 확정 화면에는 낙관적 UI를 쓰지 않는다.
