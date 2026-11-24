"""R0-08 시나리오 10: pnpm 공급망 설정을 지우거나 약하게 바꾸면 실패한다(A5-12).

- 새 판 대기(minimumReleaseAge)는 7일(10080분) 이상, 신뢰 하락 막기(trustPolicy)는 no-downgrade.
- 기본 보호를 끄거나 예외를 여는 설정 이름은 값과 상관없이 어디에도 없어야 한다(대소문자·따옴표 무시).
  예외가 꼭 필요하면 이 검사를 함께 고치는 PR로만 연다(사람이 읽고 승인한다).
- pnpm 설정이 들어갈 수 있는 다른 곳(.npmrc, package.json의 pnpm 칸)은 쓰지 않는다.
"""

import json
import re
import sys
from pathlib import Path

FRONTEND = Path("frontend")
WORKSPACE = FRONTEND / "pnpm-workspace.yaml"
FORBIDDEN_NAMES = [
    "strictDepBuilds",
    "blockExoticSubdeps",
    "dangerouslyAllowAllBuilds",
    "minimumReleaseAgeStrict",
    "minimumReleaseAgeExclude",
    "minimumReleaseAgeIgnoreMissingTime",
    "trustPolicyExclude",
    "trustPolicyIgnoreAfter",
    "allowBuilds",
    "onlyBuiltDependencies",
    "neverBuiltDependencies",
    "ignoredBuiltDependencies",
]


def value_of(text: str, key: str):
    match = re.search(rf"""^["']?{key}["']?\s*:\s*(.+?)\s*(#.*)?$""", text, re.MULTILINE | re.IGNORECASE)
    return match.group(1).strip("'\"") if match else None


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    text = WORKSPACE.read_text(encoding="utf-8")
    problems = []
    age = value_of(text, "minimumReleaseAge")
    if age is None or not age.isdigit() or int(age) < 10080:
        problems.append(f"minimumReleaseAge는 10080(7일) 이상이어야 한다: {age}")
    if (value_of(text, "trustPolicy") or "").lower() != "no-downgrade":
        problems.append(f"trustPolicy는 no-downgrade여야 한다: {value_of(text, 'trustPolicy')}")
    code = re.sub(r"#.*", "", text)  # 주석 속 설명은 빼고 본다
    for name in FORBIDDEN_NAMES:
        if re.search(rf"\b{name}\b", code, re.IGNORECASE):
            problems.append(f"{name}은(는) 쓰지 않는다(기본 보호를 끄거나 예외를 연다)")
    for npmrc in FRONTEND.rglob(".npmrc"):
        if "node_modules" not in npmrc.parts:
            problems.append(f"{npmrc}: pnpm 설정은 pnpm-workspace.yaml에만 둔다")
    for manifest in FRONTEND.rglob("package.json"):
        if "node_modules" in manifest.parts:
            continue
        if "pnpm" in json.loads(manifest.read_text(encoding="utf-8")):
            problems.append(f"{manifest}: package.json의 pnpm 칸은 쓰지 않는다")
    for problem in problems:
        print(problem)
    print(f"pnpm 공급망 설정 검사: 문제 {len(problems)}건")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
