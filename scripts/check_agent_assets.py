#!/usr/bin/env python3
"""检查模块规则与仓库 skills 元数据，不把内容匹配当行为验收。"""

import argparse
import re
import sys
from pathlib import Path

from check_docs import link_errors


def violations(root):
    rules = [root / "AGENTS.md"]
    settings = root / "settings.gradle.kts"
    if not settings.is_file():
        yield "缺少 settings.gradle.kts"
        return
    modules = re.findall(r'include\(":([^"]+)"\)', settings.read_text())
    rules.extend(root / module.replace(":", "/") / "AGENTS.md" for module in modules)
    for rule in rules:
        if not rule.is_file():
            yield f"缺少模块规则：{rule.relative_to(root)}"
    skills = sorted((root / ".agents/skills").glob("*/SKILL.md"))
    if not skills:
        yield "缺少仓库 skills"
    names = set()
    for skill in skills:
        content = skill.read_text(encoding="utf-8")
        frontmatter = re.match(r"\A---\n(.+?)\n---(?:\n|$)", content, re.DOTALL)
        if not frontmatter:
            yield f"Skill 缺少 frontmatter：{skill.relative_to(root)}"
            continue
        name = re.search(r"^name:\s*([a-z0-9-]+)\s*$", frontmatter[1], re.MULTILINE)
        description = re.search(r"^description:\s*(.+)$", frontmatter[1], re.MULTILINE)
        if not name or name[1] != skill.parent.name or len(name[1]) > 64:
            yield f"Skill name 与目录不一致：{skill.relative_to(root)}"
        if name:
            if name[1] in names:
                yield f"Skill name 重复：{name[1]}"
            names.add(name[1])
        if not description or not 1 <= len(description[1]) <= 1024:
            yield f"Skill description 非法：{skill.relative_to(root)}"
    yield from link_errors(root, [file for file in rules + skills if file.is_file()])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    errors = list(violations(parser.parse_args().root.resolve()))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("模块 AGENTS 与仓库 Skill 资产检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
