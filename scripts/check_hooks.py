#!/usr/bin/env python3
"""检查 Lefthook 安装入口及本地工具可用性。"""

import json
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    for name in ("pre-commit", "commit-msg"):
        path = subprocess.check_output(
            ["git", "rev-parse", "--git-path", f"hooks/{name}"], text=True, cwd=ROOT
        ).strip()
        hook = Path(path)
        if not hook.is_absolute():
            hook = ROOT / hook
        if not hook.is_file() or "lefthook" not in hook.read_text().lower():
            raise RuntimeError(f"{name} 未安装 Lefthook；运行 pnpm hooks:install")
        if './node_modules/.bin/lefthook "$@"' not in hook.read_text():
            raise RuntimeError(f"{name} 未绑定本地锁定版本；重新运行 pnpm hooks:install")
        if not hook.stat().st_mode & 0o111:
            raise RuntimeError(f"{name} 缺少执行权限")
    expected = json.loads((ROOT / "package.json").read_text())["devDependencies"]["lefthook"]
    actual = subprocess.check_output(
        ["pnpm", "exec", "lefthook", "version"], text=True, cwd=ROOT
    ).strip()
    if actual != expected:
        raise RuntimeError(f"Lefthook 版本不符：需要 {expected}，实际 {actual}")
    version = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]["ktlint"]
    for path in (
        ROOT / f".cache/dev-tools/ktlint-{version}.jar",
        ROOT / ".venv/bin/ruff",
        ROOT / "node_modules/.bin/commitlint",
        ROOT / "node_modules/.bin/prettier",
    ):
        if not path.is_file():
            raise RuntimeError(f"缺少本地工具：{path.name}；运行 pnpm install 和 pnpm tools:setup")
    print("Lefthook 入口、版本与本地工具检查通过")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
