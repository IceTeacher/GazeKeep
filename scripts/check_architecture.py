#!/usr/bin/env python3
"""检查人工约定的编译模块和源码边界，不替代 Manifest 合并或实机验证。"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
ALLOWED_PROJECTS = {
    "androidApp": {"shared", "engine", "backend:a11y", "backend:shizuku", "backend:lsposed", "xposed"},
    "shared": {"core"},
    "core": set(),
    "engine": {"core"},
    "backend:a11y": {"core"},
    "backend:shizuku": {"core"},
    "backend:lsposed": {"core", "bridge:protocol"},
    "xposed": {"bridge:protocol"},
    "bridge:protocol": set(),
}
FORBIDDEN_IMPORTS = {
    "core": ("android.", "androidx."),
    "shared": ("android.", "cn.rainafter.app.gazekeep.engine.", "cn.rainafter.app.gazekeep.backend."),
    "engine": ("cn.rainafter.app.gazekeep.backend.", "cn.rainafter.app.gazekeep.ui.",
               "cn.rainafter.app.gazekeep.runtime.", "cn.rainafter.app.gazekeep.service."),
    "xposed": ("kotlinx.coroutines.", "androidx.datastore.", "androidx.compose.", "androidx.camera.",
               "com.google.mlkit.", "java.io.", "java.nio.file.", "cn.rainafter.app.gazekeep.core.",
               "cn.rainafter.app.gazekeep.engine."),
    "bridge:protocol": ("kotlinx.coroutines.", "androidx.", "com.google.mlkit.",
                        "cn.rainafter.app.gazekeep.core.", "cn.rainafter.app.gazekeep.engine."),
    "backend:lsposed": ("cn.rainafter.app.gazekeep.xposed.power.", "cn.rainafter.app.gazekeep.xposed.hook.",
                        "cn.rainafter.app.gazekeep.xposed.entry."),
}


def violations():
    configured = set(re.findall(r'include\(":([^"]+)"\)', (ROOT / "settings.gradle.kts").read_text()))
    if configured != set(ALLOWED_PROJECTS):
        yield "settings 中的模块与边界规则不一致，请同步维护规则"
    for module, allowed in ALLOWED_PROJECTS.items():
        directory = ROOT / module.replace(":", "/")
        build = (directory / "build.gradle.kts").read_text()
        actual = set(re.findall(r'project\(":([^"]+)"\)', build))
        for dependency in sorted(actual - allowed):
            yield f"{module} 不应依赖 :{dependency}"
        for source in (directory / "src").rglob("*.kt"):
            relative = source.relative_to(directory / "src")
            contents = source.read_text()
            package = re.search(r"^package\s+([\w.]+)", contents, re.MULTILINE)
            if package and "kotlin" in relative.parts:
                kotlin_root = relative.parts.index("kotlin") + 1
                actual_directory = relative.parts[kotlin_root:-1]
                if actual_directory != tuple(package.group(1).split(".")):
                    yield f"{source.relative_to(ROOT)}: 目录与包名 {package.group(1)} 不一致"
            if "test" in relative.parts[0].lower():
                continue
            if module in {"core", "shared"} and relative.parts[0] != "commonMain":
                continue
            for number, line in enumerate(contents.splitlines(), 1):
                match = re.match(r"\s*import\s+([\w.]+)", line)
                if match and match.group(1).startswith(FORBIDDEN_IMPORTS.get(module, ())):
                    yield f"{source.relative_to(ROOT)}:{number}: 越界导入 {match.group(1)}"


if __name__ == "__main__":
    errors = list(violations())
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)
    print("架构边界与目录/包名检查通过（9 个 Gradle 模块）")
