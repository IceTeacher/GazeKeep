#!/usr/bin/env python3
"""按显式文件清单处理格式和语法；不修改暂存区，不运行 Gradle。"""

import argparse
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED_PARTS = {
    "build",
    ".git",
    ".gradle",
    ".kotlin",
    "node_modules",
    ".cache",
    ".venv",
    "__pycache__",
}
KINDS = {
    ".kt": "kotlin",
    ".kts": "kotlin",
    ".md": "text",
    ".json": "text",
    ".yaml": "text",
    ".yml": "text",
    ".js": "text",
    ".cjs": "text",
    ".mjs": "text",
    ".xml": "text",
    ".py": "python",
    ".toml": "toml",
}


def run(arguments):
    subprocess.run(arguments, check=True, cwd=Path.cwd())


def eligible(path):
    if not path.is_file() or path.is_symlink():
        return False
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"文件必须是仓库内相对路径：{path}")
    return (
        not (set(path.parts) & EXCLUDED_PARTS)
        and not path.is_relative_to("docs/archive")
        and path.name != "pnpm-lock.yaml"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=["all", "kotlin", "text", "python", "toml"])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("files", nargs="*")
    args = parser.parse_args()
    names = args.files
    if args.kind == "all" and not names:
        result = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
            check=True,
            capture_output=True,
        )
        names = [name.decode("utf-8") for name in result.stdout.split(b"\0") if name]
    groups = {}
    for name in sorted(set(names)):
        path = Path(name)
        kind = KINDS.get(path.suffix)
        if eligible(path) and kind and (args.kind == "all" or args.kind == kind):
            groups.setdefault(kind, []).append(str(path))
    for kind, files in groups.items():
        if kind == "kotlin":
            catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
            jar = ROOT / ".cache/dev-tools" / f"ktlint-{catalog['versions']['ktlint']}.jar"
            if not jar.is_file():
                raise FileNotFoundError("ktlint 未准备，请运行 pnpm tools:setup")
            arguments = ["java", "-jar", str(jar), "--editorconfig", str(ROOT / ".editorconfig")]
            run(arguments + ([] if args.check else ["--format"]) + files)
        elif kind == "text":
            for file in files:
                if Path(file).suffix in {".js", ".cjs", ".mjs"}:
                    run(["node", "--check", file])
            run(
                [
                    str(ROOT / "node_modules/.bin/prettier"),
                    "--config",
                    str(ROOT / ".prettierrc.json"),
                    "--ignore-path",
                    str(ROOT / ".prettierignore"),
                    "--check" if args.check else "--write",
                    "--",
                    *files,
                ]
            )
        elif kind == "python":
            ruff = ROOT / ".venv/bin/ruff"
            if not ruff.is_file():
                raise FileNotFoundError("Ruff 未准备，请运行 pnpm tools:setup")
            run([str(ruff), "check", "--config", str(ROOT / "pyproject.toml"), "--", *files])
            run(
                [
                    str(ruff),
                    "format",
                    "--config",
                    str(ROOT / "pyproject.toml"),
                    *(["--check"] if args.check else []),
                    "--",
                    *files,
                ]
            )
        else:
            for file in files:
                with open(file, "rb") as stream:
                    tomllib.load(stream)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"待提交文件检查失败：{error}", file=sys.stderr)
        sys.exit(1)
