"""R0-08 시나리오 10: pnpm 공급망 설정을 지우거나 약하게 바꾸면 실패한다(A5-12).

- 새 판 대기(minimumReleaseAge)는 7일(10080분) 이상
- 신뢰 하락 막기(trustPolicy)는 no-downgrade
- 기본 보호를 끄는 설정(설치 스크립트 허용, 이상한 경로 허용 등)은 없어야 한다
"""

import re
import sys
from pathlib import Path

WORKSPACE = Path("frontend/pnpm-workspace.yaml")
WEAKENING = {
    "strictDepBuilds": "false",
    "blockExoticSubdeps": "false",
    "dangerouslyAllowAllBuilds": "true",
    "minimumReleaseAgeStrict": "false",
}
FORBIDDEN_KEYS = ["minimumReleaseAgeExclude", "trustPolicyIgnoreAfter"]


def value_of(text: str, key: str):
    match = re.search(rf"^{key}:\s*(.+?)\s*(#.*)?$", text, re.MULTILINE)
    return match.group(1).strip("'\"") if match else None


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    text = WORKSPACE.read_text(encoding="utf-8")
    problems = []
    age = value_of(text, "minimumReleaseAge")
    if age is None or not age.isdigit() or int(age) < 10080:
        problems.append(f"minimumReleaseAge는 10080(7일) 이상이어야 한다: {age}")
    if value_of(text, "trustPolicy") != "no-downgrade":
        problems.append(f"trustPolicy는 no-downgrade여야 한다: {value_of(text, 'trustPolicy')}")
    for key, bad in WEAKENING.items():
        if value_of(text, key) == bad:
            problems.append(f"{key}: {bad}는 기본 보호를 끈다")
    for key in FORBIDDEN_KEYS:
        if value_of(text, key) is not None or re.search(rf"^{key}:", text, re.MULTILINE):
            problems.append(f"{key}는 쓰지 않는다(예외는 trustPolicyExclude에 정확한 판 번호로만)")
    for problem in problems:
        print(problem)
    print(f"pnpm 공급망 설정 검사: 문제 {len(problems)}건")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
