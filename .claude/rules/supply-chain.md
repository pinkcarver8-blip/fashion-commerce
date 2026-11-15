---
paths:
  - ".github/**"
  - "**/build.gradle"
  - "**/settings.gradle"
  - "**/gradle/**"
  - "**/package.json"
  - "**/pnpm-lock.yaml"
  - "**/pnpm-workspace.yaml"
---

# [엄격] 공급망: 의존성·빌드 도구·CI

원문: 작업진행.md A5-12.

- 잠금 파일을 커밋하고, CI는 고정 설치(`pnpm install --frozen-lockfile`)만 한다.
- pnpm 11의 기본 보호(공개 1일 미만 버전 설치 금지, 설치 스크립트 차단)를 끄지 않는다.
- GitHub Actions는 태그가 아니라 커밋 SHA로 고정한다. CI에서 Gradle wrapper 변조를 검증한다.
- 새 의존성은 PR에 이유를 적는다. AI가 제안한 패키지는 실제로 있는 패키지인지 확인한다(이름만 비슷한 가짜 방지).
- CI에 운영 키를 두지 않는다. 시크릿이 필요한 테스트는 PG 스텁으로 바꾼다.
