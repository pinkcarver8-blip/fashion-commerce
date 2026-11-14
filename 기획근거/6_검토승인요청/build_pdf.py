"""검토 보고서 HTML → PDF. 1차 렌더링으로 제목별 쪽 번호를 구해 목차에 넣고 다시 렌더링한다.

사용: python report/build_pdf.py
필요: Chrome(또는 Edge), pymupdf
"""
import html
import re
import subprocess
import sys
from pathlib import Path

import pymupdf

HERE = Path(__file__).resolve().parent
SRC = HERE / "기획서_v0.2_검토보고서.html"
OUT = HERE / "기획서_v0.2_검토보고서.pdf"
TMP_HTML = HERE / "_build.html"
BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]

HEADING = re.compile(r'<(h1|h2) class="sec" id="(s[^"]+)"[^>]*>(.*?)</\1>|<(h2) id="(s[^"]+)"[^>]*>(.*?)</h2>', re.S)


def text_of(fragment: str) -> str:
    fragment = re.sub(r'<span class="ref".*?</span>', "", fragment, flags=re.S)
    fragment = re.sub(r'<span class="tag.*?</span>', "", fragment, flags=re.S)
    fragment = re.sub(r'<span class="no">(.*?)</span>', r"\1. ", fragment, flags=re.S)
    return html.unescape(re.sub(r"<[^>]+>", "", fragment)).strip()


def headings(src: str):
    out = []
    for m in HEADING.finditer(src):
        if m.group(1):
            level, hid, inner = 1, m.group(2), m.group(3)
        else:
            level, hid, inner = 2, m.group(5), m.group(6)
        title = text_of(inner)
        if level == 1:
            title = re.sub(r"^([0-9A-Z]+)\.\s*", r"\1. ", title)
        out.append((level, hid, title))
    return out


def toc_html(items, pages):
    rows = []
    for level, hid, title in items:
        pg = pages.get(hid, "")
        rows.append(
            f'<li class="l{level}"><a href="#{hid}">{html.escape(title)}</a>'
            f'<span class="dots"></span><span class="pg">{pg}</span></li>'
        )
    return "\n".join(rows)


def render(src_html: str, pdf: Path):
    TMP_HTML.write_text(src_html, encoding="utf-8")
    browser = next((b for b in BROWSERS if Path(b).exists()), None)
    if not browser:
        sys.exit("Chrome/Edge를 찾지 못했다")
    subprocess.run(
        [browser, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
         "--generate-pdf-document-outline", f"--print-to-pdf={pdf}", TMP_HTML.as_uri()],
        check=True, capture_output=True, timeout=180,
    )


def norm(s: str) -> str:
    return re.sub(r"\s+", "", s)


def find_pages(pdf: Path, items):
    """PDF 각 쪽의 텍스트에서 제목을 순서대로 찾는다. 목차 쪽은 건너뛴다."""
    doc = pymupdf.open(pdf)
    texts = [norm(p.get_text()) for p in doc]
    start = next(i for i, t in enumerate(texts) if "목차" in t[:200]) + 1
    while start < len(texts) and "1.프로젝트개요" not in texts[start][:200] and "1프로젝트개요" not in texts[start][:200]:
        start += 1
    pages, cur = {}, start
    for level, hid, title in items:
        if hid == "s0" or hid.startswith("s0-"):
            key = norm(title.split(". ", 1)[-1] if level == 1 else title)
            for i in range(0, start):
                if key in texts[i]:
                    pages[hid] = i + 1
                    break
            continue
        key = norm(title.split(". ", 1)[-1] if level == 1 else title)
        for i in range(cur, len(texts)):
            if key in texts[i]:
                pages[hid] = i + 1
                cur = i
                break
        else:
            print(f"[경고] 쪽을 찾지 못함: {title}")
    return pages, len(texts)


def main():
    src = SRC.read_text(encoding="utf-8")
    items = [h for h in headings(src) if h[1] != "toc"]
    first = src.replace("<!--TOC-->", toc_html(items, {}))
    render(first, OUT)
    pages, total = find_pages(OUT, items)
    final = src.replace("<!--TOC-->", toc_html(items, pages))
    render(final, OUT)
    pages2, total2 = find_pages(OUT, items)
    if pages2 != pages:
        print("[경고] 목차를 넣은 뒤 쪽 번호가 바뀌었다. 다시 실행한다")
        render(src.replace("<!--TOC-->", toc_html(items, pages2)), OUT)
    TMP_HTML.unlink(missing_ok=True)
    print(f"완료: {OUT} ({total2}쪽, 목차 항목 {len(items)}개, 쪽 확인 {len(pages2)}개)")


if __name__ == "__main__":
    main()
