---
paths:
  - ".github/**"
  - "**/build.gradle"
  - "**/settings.gradle"
  - "**/gradle/**"
  - "**/gradlew"
  - "**/gradlew.bat"
  - "**/*.lockfile"
  - "**/lombok.config"
  - "**/Dockerfile"
  - "**/docker-compose.yml"
  - "**/.dockerignore"
  - "**/package.json"
  - "**/pnpm-lock.yaml"
  - "**/pnpm-workspace.yaml"
---

# [엄격] 공급망: 의존성·빌드 도구·CI

원문: 작업진행.md A5-12.

- 잠금 파일을 커밋하고, CI는 고정 설치(`pnpm install --frozen-lockfile`)만 한다.
- pnpm 12의 기본 보호(공개 1일 미만 버전 설치 금지, 이상한 경로 하위 패키지 차단, 설치 스크립트 차단)를 끄지 않는다.
- GitHub Actions는 태그가 아니라 커밋 SHA로 고정한다. CI에서 Gradle wrapper 변조를 검증한다.
- 새 판은 공개 7일이 지난 것만 들인다. 보안 수정 판은 바로 검토할 수 있다(PR에 어떤 수정인지 적는다). A5-12.
- 백엔드 부품을 바꾸면 `backend/gradle/verification-metadata.xml`(부품 지문 목록)을 리눅스 컨테이너에서 빈 캐시로 다시 만든다(A5-12의 명령). 늘어난 항목이 바꾼 부품뿐인지 PR에서 본다.
- 작업 흐름에서 쓰기 권한이 필요한 작업은 PR 코드를 실행하지 않는다(A5-12).
- 새 의존성은 PR에 이유를 적는다. AI가 제안한 패키지는 실제로 있는 패키지인지 확인한다(이름만 비슷한 가짜 방지).
- CI에 운영 키를 두지 않는다. 시크릿이 필요한 테스트는 PG 스텁으로 바꾼다.
- pnpm 설정(`frontend/pnpm-workspace.yaml`)의 새 판 7일 대기와 신뢰 하락 막기를 끄거나 줄이지 않는다. 컨테이너 이미지는 판 번호 + 지문으로 고정한다(A5-12).
- 검사 파일(`.github/`, `tools/ci/`, `.gitleaks.toml`, `verification-metadata.xml`, `build.gradle`, `pnpm-workspace.yaml`, `eslint.config.js`)을 바꾸는 PR은 [일반] 단위에서도 합치기 전에 사용자에게 바뀐 줄을 보여 주고 승인받는다(A3-8 ⑥).
