"""문서 검사 (작업진행.md A3-9, docs/ai/review-checklist.md).

저장소 루트에서 `python tools/doc-check/doc_check.py`로 실행한다. 표준 라이브러리만 쓴다(A5-12).

오류(종료 코드 1):
  - 저장소 안 문서 링크가 없는 파일이나 없는 제목(앵커)을 가리킨다
  - docs/adr/README.md 목록의 상태와 ADR 파일의 "상태" 줄이 다르다
경고(종료 코드에 영향 없음, 하나씩 확인한다):
  - 저장소에서 뺀 로컬 전용 파일을 가리키는 링크
  - docs/ 문서에 근거 표시 없이 일반화 단어(많다·대부분 등)가 있다
정보:
  - ADR의 미룬 일(미확인·예정·나중에 등) 목록. E4-2나 작업 행에 등록됐는지 사람이 대조한다

기획근거/는 읽기 전용 보관함이라 검사하지 않는다.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LINK = re.compile(r"\[([^\]]*)\]\(([^)\s]+)\)")
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
WEASEL = re.compile(r"(?<![가-힣])(?:많다|대부분|일반적|보통|흔히|업계|자주)")
# 같은 줄에 이런 표시가 있으면 근거를 단 것으로 본다
SOURCE_MARK = re.compile(r"§|확인|실측|R0-0\d|\]\(|ADR-\d|출처|문서|기획서|작업진행")
DEFERRED = re.compile(r"미확인|예정\(|에서 정한다|에서 확인|나중에|승인 뒤 반영")
ADR_ROW = re.compile(r"^\|\s*\[(\w+)\]\(([^)]+)\)\s*\|.*\|\s*([^|]+?)\s*\|\s*$")
ADR_STATUS = re.compile(r"^- \*\*상태\*\*:\s*(\S+)")


TRACKED = set()


def tracked_files():
    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "-z"], cwd=ROOT,
                         capture_output=True, encoding="utf-8", check=True)
    TRACKED.update(x for x in out.stdout.split(chr(0)) if x)


def tracked_markdown():
    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "-z", "*.md"], cwd=ROOT,
                         capture_output=True, encoding="utf-8", check=True)
    files = []
    for line in out.stdout.split(chr(0)):
        if not line or line.startswith("기획근거/"):
            continue
        files.append(ROOT / line)
    return files


def local_only_patterns():
    pats = []
    in_local = False
    for line in (ROOT / ".gitignore").read_text(encoding="utf-8").splitlines():
        if line.startswith("# 저장소에 올리지 않는 로컬 문서"):
            in_local = True
            continue
        if in_local:
            if not line.strip():
                break
            pats.append(line.strip().rstrip("/"))
    return pats


def slug(text):
    text = re.sub(r"`|\*|~", "", text).strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def anchors(path, cache={}):
    if path in cache:
        return cache[path]
    seen, result, fence = {}, set(), False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("```"):
            fence = not fence
            continue
        m = None if fence else HEADING.match(line)
        if not m:
            continue
        base = slug(m.group(2))
        n = seen.get(base, 0)
        seen[base] = n + 1
        result.add(base if n == 0 else f"{base}-{n}")
    cache[path] = result
    return result


def check_links(files, local_only, errors, warnings):
    for f in files:
        fence = False
        for no, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("```"):
                fence = not fence
            if fence:
                continue
            for _, target in LINK.findall(line):
                if re.match(r"^[a-z]+:", target):
                    continue
                path_part, _, anchor = target.partition("#")
                where = f"{f.relative_to(ROOT).as_posix()}:{no}"
                dest = (f.parent / path_part).resolve() if path_part else f
                rel = dest.relative_to(ROOT).as_posix() if dest.is_relative_to(ROOT) else path_part
                if path_part and (not dest.exists() or (dest.is_file() and rel not in TRACKED)):
                    if any(rel == p or rel.startswith(p + "/") for p in local_only):
                        warnings.append(f"{where} 로컬 전용 파일 링크(공개 저장소에서 열리지 않음): {target}")
                    else:
                        errors.append(f"{where} 없는 파일: {target}")
                    continue
                if anchor and dest.suffix == ".md" and anchor not in anchors(dest):
                    errors.append(f"{where} 없는 제목 앵커: {target}")


def check_adr_index(errors):
    adr_dir = ROOT / "docs" / "adr"
    listed = set()
    for line in (adr_dir / "README.md").read_text(encoding="utf-8").splitlines():
        m = ADR_ROW.match(line)
        if not m:
            continue
        num, file, status = m.groups()
        listed.add(file)
        path = adr_dir / file
        if not path.exists():
            errors.append(f"docs/adr/README.md ADR-{num} 파일 없음: {file}")
            continue
        actual = next((ADR_STATUS.match(l).group(1) for l in path.read_text(encoding="utf-8").splitlines()
                       if ADR_STATUS.match(l)), None)
        if actual != status:
            errors.append(f"docs/adr/README.md ADR-{num} 상태 불일치: 목록 '{status}', 파일 '{actual}'")
    for path in adr_dir.glob("[0-9]*.md"):
        if path.name not in listed:
            errors.append(f"docs/adr/{path.name}이 목록(README.md)에 없음")


def check_weasel(files, warnings):
    for f in files:
        rel = f.relative_to(ROOT).as_posix()
        if not rel.startswith("docs/") or rel == "docs/ai/review-checklist.md":
            continue
        for no, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if WEASEL.search(line) and not SOURCE_MARK.search(line):
                warnings.append(f"{rel}:{no} 근거 표시 없는 일반화 단어: {line.strip()[:80]}")


def list_deferred():
    items = []
    for path in sorted((ROOT / "docs" / "adr").glob("[0-9]*.md")):
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if DEFERRED.search(line):
                items.append(f"docs/adr/{path.name}:{no} {line.strip()[:90]}")
    return items


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    tracked_files()
    files = tracked_markdown()
    errors, warnings = [], []
    check_links(files, local_only_patterns(), errors, warnings)
    check_adr_index(errors)
    check_weasel(files, warnings)
    deferred = list_deferred()
    for title, items in (("오류", errors), ("경고", warnings)):
        print(f"== {title} {len(items)}건")
        for item in items:
            print("  " + item)
    print(f"== 정보: ADR의 미룬 일 {len(deferred)}건 (E4-2·작업 행 등록 여부를 사람이 대조)")
    if "--deferred" in sys.argv:
        for item in deferred:
            print("  " + item)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
