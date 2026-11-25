"""R0-08 시나리오 9: 작업 흐름의 외부 Actions와 컨테이너 이미지는 지문으로 고정한다(A5-12).

- `uses:` 값은 `소유자/저장소@40자리 SHA`여야 한다. `docker://`는 `@sha256:` 지문이 있어야 한다.
- 작업 흐름의 `image:`(container·services)도 `@sha256:` 지문이 있어야 한다.
- 따옴표로 감싼 키, 한 줄 표기({uses: ...}), 값을 다음 줄에 쓴 경우도 찾는다.
- .github 아래 모든 YAML과 저장소 어디에 있든 action.yml을 본다.
태그(@v4)는 나중에 다른 코드를 가리키도록 옮길 수 있어서, 같은 이름으로 다른 코드가 실행될 수 있다.
"""

import re
import sys
from pathlib import Path

USES = re.compile(r"""["']?\buses\b["']?\s*:\s*(?:\n\s*)?["']?([^\s"',}#]+)""")
IMAGE = re.compile(r"""["']?\bimage\b["']?\s*:\s*(?:\n\s*)?["']?([^\s"',}#]+)""")
PINNED = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
DIGEST = re.compile(r"@sha256:[0-9a-f]{64}$")


def files():
    found = set(Path(".github").rglob("*.yml")) | set(Path(".github").rglob("*.yaml"))
    for name in ("action.yml", "action.yaml"):
        found |= {p for p in Path(".").rglob(name) if "node_modules" not in p.parts}
    return sorted(found)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    problems = []
    for path in files():
        text = path.read_text(encoding="utf-8")
        for match in USES.finditer(text):
            target = match.group(1)
            line = text.count("\n", 0, match.start()) + 1
            if target.startswith("./"):
                continue  # 저장소 안의 로컬 Action(이 검사가 함께 본다)
            if target.startswith("docker://"):
                ok = bool(DIGEST.search(target))
            else:
                ok = bool(PINNED.match(target))
            if not ok:
                problems.append(f"{path}:{line}: 지문으로 고정하지 않았다: {target}")
        if path.parts[:2] == (".github", "workflows"):
            for match in IMAGE.finditer(text):
                target = match.group(1)
                line = text.count("\n", 0, match.start()) + 1
                if not DIGEST.search(target):
                    problems.append(f"{path}:{line}: 이미지를 지문으로 고정하지 않았다: {target}")
    for problem in problems:
        print(problem)
    print(f"Actions·이미지 고정 검사: 문제 {len(problems)}건")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
