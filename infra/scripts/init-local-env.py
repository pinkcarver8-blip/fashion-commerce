"""infra/.env를 무작위 비밀값으로 만들거나, 빠진 변수만 더한다(로컬 전용, A5-10).

- 값을 화면에 출력하지 않는다. 이미 있는 값은 읽어서 보여 주거나 바꾸지 않는다(변수 이름만 본다).
- 사용법(저장소 맨 위에서):
  - 처음: `python infra/scripts/init-local-env.py`
  - 이미 infra/.env가 있을 때 새 변수만 더하기: `python infra/scripts/init-local-env.py --add-missing`
"""

import base64
import secrets
import sys
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


def password() -> str:
    return secrets.token_urlsafe(24)


def jwt_key() -> str:
    # 고객 토큰 서명 키: 32바이트(256비트) 무작위 값의 base64(ADR-008 결정 2).
    return base64.b64encode(secrets.token_bytes(32)).decode("ascii")


GENERATORS = {
    "MYSQL_PASSWORD": password,
    "MYSQL_ROOT_PASSWORD": password,
    "REDIS_PASSWORD": password,
    "STORE_JWT_SECRET": jwt_key,
}


def existing_names(text: str) -> set[str]:
    names = set()
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            names.add(line.split("=", 1)[0].strip())
    return names


def main(args: list[str]) -> int:
    # Windows 콘솔에서도 한글 안내가 깨지지 않게 한다.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    add_missing = "--add-missing" in args
    if ENV_FILE.exists() and not add_missing:
        print(f"{ENV_FILE}이 이미 있어 그대로 둔다. 새 변수만 더하려면 --add-missing을 붙인다.")
        return 1
    if not ENV_FILE.exists():
        lines = [f"{name}={make()}" for name, make in GENERATORS.items()]
        ENV_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{ENV_FILE}을 만들었다. 변수 {len(lines)}개(값은 출력하지 않는다).")
        return 0
    text = ENV_FILE.read_text(encoding="utf-8")
    missing = [name for name in GENERATORS if name not in existing_names(text)]
    if not missing:
        print("더할 변수가 없다.")
        return 0
    addition = "".join(f"{name}={GENERATORS[name]()}\n" for name in missing)
    prefix = "" if text.endswith("\n") or not text else "\n"
    with ENV_FILE.open("a", encoding="utf-8") as env:
        env.write(prefix + addition)
    print(f"변수 {len(missing)}개를 더했다: {', '.join(missing)}(값은 출력하지 않는다).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
