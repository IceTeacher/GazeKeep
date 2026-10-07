#!/usr/bin/env python3
"""校验活动文档索引、计划日期及本地引用；归档不参与活动门禁。"""

import argparse
import datetime
import re
import subprocess
import sys
import unicodedata
from pathlib import Path
from urllib.parse import unquote, urlsplit

IGNORED = {".git", ".gradle", ".kotlin", "node_modules", "build", ".cache", ".venv", "__pycache__"}


def markdown_files(root):
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        capture_output=True,
    )
    if result.returncode == 0:
        candidates = {root / name.decode("utf-8") for name in result.stdout.split(b"\0") if name}
    else:
        candidates = set(root.rglob("*.md"))
    return sorted(
        path
        for path in candidates
        if path.is_file()
        and path.suffix == ".md"
        and not (set(path.relative_to(root).parts) & IGNORED)
    )


def prose(contents):
    lines = []
    fence = None
    for line in contents.splitlines():
        match = re.match(r"^\s*(`{3,}|~{3,})", line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = (marker[0], len(marker))
            elif marker[0] == fence[0] and len(marker) >= fence[1]:
                fence = None
            continue
        if fence is None:
            lines.append(line)
    return "\n".join(lines)


def links(contents):
    contents = prose(contents)
    pattern = r"!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+[\"'][^\n]*?[\"'])?\s*\)"
    for match in re.finditer(pattern, contents):
        yield match.group(1).strip("<>")
    for match in re.finditer(r"^\s*\[[^\]]+\]:\s*(<[^>]+>|\S+)", contents, re.MULTILINE):
        yield match.group(1).strip("<>")


def anchors(contents):
    found = set()
    counts = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", prose(contents), re.MULTILINE):
        heading = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", heading).lower()
        slug = "".join(c for c in heading if c in " _-" or unicodedata.category(c)[0] in {"L", "N"})
        slug = slug.replace(" ", "-")
        count = counts.get(slug, 0)
        found.add(f"{slug}-{count}" if count else slug)
        counts[slug] = count + 1
    found.update(re.findall(r'(?:id|name)=["\']([^"\']+)', contents))
    return found


def link_errors(root, files):
    for page in files:
        for link in links(page.read_text(encoding="utf-8")):
            parsed = urlsplit(link)
            if parsed.scheme or parsed.netloc:
                continue
            target = (
                (page.parent / unquote(parsed.path)).resolve() if parsed.path else page.resolve()
            )
            if not target.is_relative_to(root):
                yield f"{page.relative_to(root)}: 引用越出仓库：{link}"
            elif not target.exists():
                yield f"{page.relative_to(root)}: 引用不存在：{link}"
            elif parsed.fragment and target.suffix == ".md" and target.is_file():
                if unquote(parsed.fragment) not in anchors(target.read_text(encoding="utf-8")):
                    yield f"{page.relative_to(root)}: 锚点不存在：{link}"


def violations(root):
    index = root / "docs/index.md"
    if not index.is_file():
        yield "缺少 docs/index.md"
        return
    registrations = {}
    for line in index.read_text(encoding="utf-8").splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if not line.lstrip().startswith("|") or len(cells) != 5:
            continue
        targets = list(links(cells[0]))
        if not targets:
            continue
        target = (index.parent / unquote(targets[0])).resolve()
        if target in registrations:
            yield f"索引重复登记：{target.relative_to(root)}"
        registrations[target] = cells[-1]
        try:
            datetime.date.fromisoformat(cells[-1])
        except ValueError:
            yield f"复核日期非法：{cells[0]}"
    files = markdown_files(root)
    active = [page for page in files if not page.is_relative_to(root / "docs/archive")]
    for page in active:
        if (
            page.is_relative_to(root / "docs")
            and page != index
            and page.resolve() not in registrations
        ):
            yield f"活动文档未登记：{page.relative_to(root)}"
        if page.is_relative_to(root / "docs/plans"):
            name = re.fullmatch(r"(\d{8})-[a-z0-9-]+\.md", page.name)
            dates = re.findall(
                r"^最后维护：(\d{4}-\d{2}-\d{2})\s*$", page.read_text(), re.MULTILINE
            )
            if not name or len(dates) != 1:
                yield f"计划文件名或维护日期行非法：{page.relative_to(root)}"
            elif (
                name[1] != dates[0].replace("-", "")
                or registrations.get(page.resolve()) != dates[0]
            ):
                yield f"计划三处日期不一致：{page.relative_to(root)}"
    yield from link_errors(root, active)
    git_root = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--show-toplevel"], capture_output=True, text=True
    )
    if git_root.returncode == 0 and Path(git_root.stdout.strip()).resolve() == root:
        changes = subprocess.run(
            ["git", "-C", str(root), "diff", "HEAD", "--name-only", "-z"], capture_output=True
        )
        others = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--others", "--exclude-standard", "-z"],
            capture_output=True,
        )
        names = {n.decode() for n in (changes.stdout + others.stdout).split(b"\0") if n}
        relevant = [
            n
            for n in names
            if n.startswith("docs/")
            and n.endswith(".md")
            and not n.startswith("docs/archive/")
            and n != "docs/index.md"
        ]
        if relevant and "docs/index.md" not in names:
            yield "活动文档变更未同步 docs/index.md"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    root = parser.parse_args().root.resolve()
    errors = list(violations(root))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("文档索引、计划日期与活动引用检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
