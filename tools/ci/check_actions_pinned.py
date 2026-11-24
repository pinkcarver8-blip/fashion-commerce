"""R0-08 시나리오 9: 작업 흐름의 외부 Actions는 커밋 SHA(40자리)로 고정한다(A5-12).

태그(@v4)는 나중에 다른 코드를 가리키도록 옮길 수 있어서, 같은 이름으로 다른 코드가 실행될 수 있다.
"""

import re
import sys
from pathlib import Path

USES = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)")
PINNED = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    problems = []
    for path in sorted(Path(".github").rglob("*.y*ml")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = USES.match(line)
            if not match:
                continue
            target = match.group(1).strip("'\"")
            if target.startswith("./"):
                continue  # 저장소 안의 로컬 Action
            if not PINNED.match(target):
                problems.append(f"{path}:{number}: 커밋 SHA로 고정하지 않았다: {target}")
    for problem in problems:
        print(problem)
    print(f"Actions 고정 검사: 문제 {len(problems)}건")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
