import { readFileSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { cli, context, isMain, report, walk } from "./common.ts";

const ALLOWED: Record<string, string[]> = {
  android: ["shared", "engine", "backend:a11y", "backend:shizuku", "backend:lsposed", "xposed"],
  shared: ["core"],
  core: [],
  engine: ["core"],
  "backend:a11y": ["core"],
  "backend:shizuku": ["core"],
  "backend:lsposed": ["core", "bridge:protocol"],
  xposed: ["bridge:protocol", "hidden-api-stubs"],
  "hidden-api-stubs": [],
  "bridge:protocol": [],
};
const FORBIDDEN: Record<string, string[]> = {
  core: ["android.", "androidx."],
  shared: ["android.", "cn.rainafter.app.gazekeep.engine.", "cn.rainafter.app.gazekeep.backend."],
  engine: [
    "cn.rainafter.app.gazekeep.backend.",
    "cn.rainafter.app.gazekeep.ui.",
    "cn.rainafter.app.gazekeep.runtime.",
    "cn.rainafter.app.gazekeep.service.",
  ],
  xposed: [
    "kotlinx.coroutines.",
    "androidx.datastore.",
    "androidx.compose.",
    "androidx.camera.",
    "com.google.mlkit.",
    "java.io.",
    "java.nio.file.",
    "cn.rainafter.app.gazekeep.core.",
    "cn.rainafter.app.gazekeep.engine.",
  ],
  "bridge:protocol": [
    "kotlinx.coroutines.",
    "androidx.",
    "com.google.mlkit.",
    "cn.rainafter.app.gazekeep.core.",
    "cn.rainafter.app.gazekeep.engine.",
  ],
  "backend:lsposed": [
    "cn.rainafter.app.gazekeep.xposed.power.",
    "cn.rainafter.app.gazekeep.xposed.hook.",
    "cn.rainafter.app.gazekeep.xposed.entry.",
  ],
};
export function violations(root: string) {
  const android = join(root, "apps/app");
  const errors: string[] = [];
  const modules = new Set(
    [...readFileSync(join(android, "settings.gradle.kts"), "utf8").matchAll(/include\(":([^"]+)"\)/g)].map((m) => m[1]),
  );
  if (modules.size !== Object.keys(ALLOWED).length || [...modules].some((m) => !Object.hasOwn(ALLOWED, m)))
    errors.push("settings 中的模块与边界规则不一致，请同步维护规则");
  for (const [module, allowed] of Object.entries(ALLOWED)) {
    const directory = join(android, module.replaceAll(":", "/"));
    const build = readFileSync(join(directory, "build.gradle.kts"), "utf8");
    for (const match of build.matchAll(/(\w+)\(project\(":hidden-api-stubs"\)\)/g))
      if (match[1] !== "compileOnly") errors.push(`${module}: 存根只能 compileOnly，禁止进入 APK`);
    for (const match of readFileSync(join(directory, "build.gradle.kts"), "utf8").matchAll(/project\(":([^"]+)"\)/g))
      if (!allowed.includes(match[1])) errors.push(`${module} 不应依赖 :${match[1]}`);
    for (const source of walk(join(directory, "src")).filter((n) => n.endsWith(".kt"))) {
      const parts = relative(join(directory, "src"), source).split(sep);
      const content = readFileSync(source, "utf8");
      const pkg = /^package\s+([\w.]+)/m.exec(content)?.[1];
      const kotlin = parts.indexOf("kotlin");
      if (pkg && kotlin >= 0 && parts.slice(kotlin + 1, -1).join(".") !== pkg)
        errors.push(`${relative(root, source)}: 目录与包名 ${pkg} 不一致`);
      if (parts[0].toLowerCase().includes("test") || (["core", "shared"].includes(module) && parts[0] !== "commonMain"))
        continue;
      content.split("\n").forEach((line, i) => {
        const imported = /^\s*import\s+([\w.]+)/.exec(line)?.[1];
        if (imported && (FORBIDDEN[module] ?? []).some((prefix) => imported.startsWith(prefix)))
          errors.push(`${relative(root, source)}:${i + 1}: 越界导入 ${imported}`);
      });
    }
  }
  return errors;
}
if (isMain(import.meta.url))
  await cli(() => report(violations(context().root), "架构边界与目录/包名检查通过（10 个 Gradle 模块）"));
