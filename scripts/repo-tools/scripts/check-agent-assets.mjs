import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";
import { cli, context, isMain, report } from "./common.mjs";
import { linkErrors } from "./check-docs.mjs";

export function violations(root) {
  const settings = join(root, "apps/app/settings.gradle.kts");
  const errors = [];
  if (!existsSync(settings)) return ["缺少 apps/app/settings.gradle.kts"];
  const modules = [...readFileSync(settings, "utf8").matchAll(/include\(":([^"]+)"\)/g)].map(
    (m) => m[1],
  );
  const rules = [
    "AGENTS.md",
    "apps/app/AGENTS.md",
    "apps/web/AGENTS.md",
    "scripts/repo-tools/AGENTS.md",
    ...modules.map((m) => `apps/app/${m.replaceAll(":", "/")}/AGENTS.md`),
  ].map((n) => join(root, n));
  for (const rule of rules)
    if (!existsSync(rule)) errors.push(`缺少模块规则：${relative(root, rule)}`);
  const dir = join(root, ".agents/skills");
  const skills = existsSync(dir)
    ? readdirSync(dir)
        .map((n) => join(dir, n, "SKILL.md"))
        .filter(existsSync)
    : [];
  if (!skills.length) errors.push("缺少仓库 skills");
  const names = new Set();
  for (const skill of skills) {
    const content = readFileSync(skill, "utf8");
    const prefix = relative(root, skill);
    const frontmatter = /^---\n([\s\S]+?)\n---(?:\n|$)/.exec(content);
    if (!frontmatter) {
      errors.push(`Skill 缺少 frontmatter：${prefix}`);
      continue;
    }
    const name = /^name:\s*([a-z0-9-]+)\s*$/m.exec(frontmatter[1])?.[1];
    const description = /^description:\s*(.+)$/m.exec(frontmatter[1])?.[1];
    if (!name || name !== prefix.split("/").at(-2) || name.length > 64)
      errors.push(`Skill name 与目录不一致：${prefix}`);
    if (name) {
      if (names.has(name)) errors.push(`Skill name 重复：${name}`);
      names.add(name);
    }
    if (!description || description.length > 1024) errors.push(`Skill description 非法：${prefix}`);
  }
  errors.push(...linkErrors(root, [...rules.filter(existsSync), ...skills]));
  return errors;
}
if (isMain(import.meta.url))
  await cli(() => report(violations(context().root), "模块 AGENTS 与仓库 Skill 资产检查通过"));
