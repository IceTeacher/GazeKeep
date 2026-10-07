#!/usr/bin/env python3
"""显式安装仓库本地开发工具；Hook 自身不联网下载。"""

import hashlib
import subprocess
import sys
import tomllib
import urllib.request
import venv
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
    version = catalog["versions"]["ktlint"]
    destination = ROOT / ".cache/dev-tools" / f"ktlint-{version}.jar"
    url = f"https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/{version}/ktlint-cli-{version}-all.jar"
    checksum = (
        urllib.request.urlopen(url + ".sha256", timeout=60).read().decode().strip().split()[0]
    )
    if not destination.exists() or hashlib.sha256(destination.read_bytes()).hexdigest() != checksum:
        destination.parent.mkdir(parents=True, exist_ok=True)
        data = urllib.request.urlopen(url, timeout=60).read()
        if hashlib.sha256(data).hexdigest() != checksum:
            raise RuntimeError("ktlint 下载摘要不匹配")
        destination.write_bytes(data)
    environment = ROOT / ".venv"
    if not (environment / "bin/python").exists():
        venv.create(environment, with_pip=True)
    subprocess.run(
        [
            str(environment / "bin/python"),
            "-m",
            "pip",
            "install",
            "-r",
            str(ROOT / "requirements-dev.txt"),
        ],
        check=True,
        cwd=ROOT,
    )
    subprocess.run(["java", "-jar", str(destination), "--version"], check=True, cwd=ROOT)
    print("本地工具准备完成；使用 pnpm hooks:install 显式启用 Hook")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"开发工具安装失败：{error}", file=sys.stderr)
        sys.exit(1)
