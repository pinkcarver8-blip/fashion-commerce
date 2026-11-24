"""infra/.env를 무작위 비밀번호로 만든다(로컬 전용, A5-10).

- 이미 있으면 덮어쓰지 않고 멈춘다.
- 값을 화면에 출력하지 않는다.
- 사용법: 저장소 맨 위에서 `python infra/scripts/init-local-env.py`
"""

import secrets
import sys
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"
NAMES = ["MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "REDIS_PASSWORD"]


def main() -> int:
    if ENV_FILE.exists():
        print(f"{ENV_FILE}이 이미 있어 그대로 둔다.")
        return 1
    lines = [f"{name}={secrets.token_urlsafe(24)}" for name in NAMES]
    ENV_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{ENV_FILE}을 만들었다. 변수 {len(NAMES)}개(값은 출력하지 않는다).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
