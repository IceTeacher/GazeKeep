#!/usr/bin/env python3
"""显式安装本仓库 Hook，不覆盖已有管理器或自定义 hooksPath。"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    configuration = subprocess.run(
        ["git", "config", "--get", "core.hooksPath"], cwd=ROOT, capture_output=True, text=True
    )
    if configuration.returncode not in (0, 1):
        raise RuntimeError("无法读取 core.hooksPath")
    if configuration.returncode == 0:
        raise RuntimeError(f"已有 core.hooksPath={configuration.stdout.strip()}；请先核对原管理器")
    for name in ("pre-commit", "commit-msg"):
        path = Path(
            subprocess.check_output(
                ["git", "rev-parse", "--git-path", f"hooks/{name}"], cwd=ROOT, text=True
            ).strip()
        )
        if not path.is_absolute():
            path = ROOT / path
        if path.exists() and (not path.is_file() or "lefthook" not in path.read_text().lower()):
            raise RuntimeError(f"已有 {name} Hook；保留原文件，请先核对集成方式")
    subprocess.run(
        [str(ROOT / "node_modules/.bin/lefthook"), "install", "pre-commit", "commit-msg"],
        cwd=ROOT,
        check=True,
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Hook 安装失败：{error}", file=sys.stderr)
        sys.exit(1)
