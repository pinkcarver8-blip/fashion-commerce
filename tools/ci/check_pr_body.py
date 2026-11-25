"""R0-08 시나리오 6: PR 설명의 AI 기록 네 칸이 하나라도 비면 실패한다(작업진행.md A3-2의 6).

GitHub가 넘겨주는 이벤트 파일(GITHUB_EVENT_PATH)에서 PR 설명을 읽는다. 주석(<!-- -->)만 있으면 빈칸으로 본다.
"""

import json
import os
import re
import sys

SECTIONS = ["AI 사용 여부", "AI 제안", "내 판단", "검증 방법과 결과"]


def section_text(body: str, title: str) -> str:
    match = re.search(rf"^###\s*{re.escape(title)}[^\n]*\n(.*?)(?=^#{{1,3}}\s|\Z)", body, re.MULTILINE | re.DOTALL)
    if not match:
        return ""
    # 닫히지 않은 주석(<!--)은 GitHub 화면에서 뒤를 모두 가리므로 끝까지 지운다.
    text = re.sub(r"<!--.*?(-->|\Z)", "", match.group(1), flags=re.DOTALL)
    # 글자나 숫자가 하나도 없으면(예: "-") 빈칸으로 본다.
    return text.strip() if re.search(r"[0-9A-Za-z가-힣]", text) else ""


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as handle:
        body = (json.load(handle).get("pull_request") or {}).get("body") or ""
    empty = [title for title in SECTIONS if not section_text(body, title)]
    for title in empty:
        print(f"PR 설명의 '{title}' 칸이 비어 있다")
    print(f"AI 기록 검사: 빈칸 {len(empty)}개")
    return 1 if empty else 0


if __name__ == "__main__":
    sys.exit(main())
