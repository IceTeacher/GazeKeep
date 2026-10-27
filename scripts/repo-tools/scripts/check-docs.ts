import { existsSync, readFileSync, realpathSync, statSync } from "node:fs";
import { dirname, extname, join, relative, resolve, sep } from "node:path";
import { cli, context, IGNORED, isMain, report, run, walk, within } from "./common.ts";

export function prose(contents: string) {
  const lines: string[] = [];
  let fence: string | undefined;
  for (const line of contents.split(/\r?\n/)) {
    const match = /^\s*(`{3,}|~{3,})/.exec(line);
    if (match) {
      const marker = match[1];
      if (!fence) fence = marker;
      else if (marker[0] === fence[0] && marker.length >= fence.length) fence = undefined;
      continue;
    }
    if (!fence) lines.push(line);
  }
  return lines.join("\n");
}
export function links(contents: string) {
  const text = prose(contents);
  return [
    ...text.matchAll(/!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+["'][^\n]*?["'])?\s*\)/g),
    ...text.matchAll(/^\s*\[[^\]]+\]:\s*(<[^>]+>|\S+)/gm),
  ].map((m) => m[1].replace(/^<|>$/g, ""));
}
export function anchors(contents: string) {
  const found = new Set<string>();
  const counts = new Map<string, number>();
  for (const match of prose(contents).matchAll(/^#{1,6}\s+(.+?)\s*#*\s*$/gm)) {
    const heading = match[1].replace(/\[([^\]]+)\]\([^)]+\)/g, "$1").toLowerCase();
    const slug = [...heading]
      .filter((c) => /[ _-]|[\p{L}\p{N}]/u.test(c))
      .join("")
      .replaceAll(" ", "-");
    const count = counts.get(slug) ?? 0;
    found.add(count ? `${slug}-${count}` : slug);
    counts.set(slug, count + 1);
  }
  for (const match of contents.matchAll(/(?:id|name)=["']([^"']+)/g)) found.add(match[1]);
  return found;
}
export function linkErrors(root: string, files: string[]) {
  const errors: string[] = [];
  for (const page of files)
    for (const link of links(readFileSync(page, "utf8"))) {
      if (/^[a-z][a-z0-9+.-]*:|^\/\//i.test(link)) continue;
      const hash = link.indexOf("#");
      const path = decodeURIComponent((hash < 0 ? link : link.slice(0, hash)).split("?")[0]);
      const fragment = hash < 0 ? "" : decodeURIComponent(link.slice(hash + 1));
      let target = path ? resolve(dirname(page), path) : page;
      if (existsSync(target)) target = realpathSync(target);
      const prefix = relative(root, page);
      if (!within(root, target)) errors.push(`${prefix}: 引用越出仓库：${link}`);
      else if (!existsSync(target)) errors.push(`${prefix}: 引用不存在：${link}`);
      else if (
        fragment &&
        extname(target) === ".md" &&
        statSync(target).isFile() &&
        !anchors(readFileSync(target, "utf8")).has(fragment)
      )
        errors.push(`${prefix}: 锚点不存在：${link}`);
    }
  return errors;
}
function markdownFiles(root: string) {
  const result = run("git", ["-C", root, "ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
    capture: true,
    check: false,
  });
  const files =
    result.status === 0
      ? result.stdout
          .split("\0")
          .filter(Boolean)
          .map((n) => join(root, n))
      : walk(root);
  return [...new Set(files)]
    .filter(
      (p) =>
        extname(p) === ".md" &&
        existsSync(p) &&
        statSync(p).isFile() &&
        !relative(root, p)
          .split(sep)
          .some((n) => IGNORED.has(n)),
    )
    .sort();
}
function validDate(date: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date.startsWith("0000")) return false;
  const parsed = new Date(`${date}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date;
}
// 发布说明作为 GitHub Release 正文：安装包一节由 release.yml 生成，验收记录维护在任务清单，不进入正文。
const RELEASE_SECTIONS = ["更新日志", "启用步骤"];
function releaseNotesErrors(name: string, text: string) {
  const errors: string[] = [];
  if (!/^docs\/releases\/v\d+\.\d+\.\d+\.md$/.test(name)) errors.push(`${name}: 发布说明文件名必须为 v<版本>.md`);
  let fenced = false;
  const headings: string[] = [];
  for (const line of text.split("\n")) {
    if (/^\s*(```|~~~)/.test(line)) fenced = !fenced;
    else if (!fenced && /^#{1,2} /.test(line)) headings.push(line);
  }
  if (headings.join("\n") !== RELEASE_SECTIONS.map((s) => `## ${s}`).join("\n"))
    errors.push(`${name}: 发布说明只能依次包含「## 更新日志」「## 启用步骤」两节，不含一级标题`);
  return errors;
}
const INDEX = ".agents/docs-index.md";
function isManagedDocument(name: string) {
  return name.endsWith(".md") && name !== INDEX && (name.startsWith("docs/") || /^\.agents\/[^/]+\.md$/.test(name));
}
export function violations(root: string) {
  const index = join(root, INDEX);
  const errors: string[] = [];
  const registrations = new Map<string, string>();
  if (!existsSync(index)) return [`缺少 ${INDEX}`];
  if (!existsSync(join(root, "docs/index.md"))) errors.push("缺少 docs/index.md 读者导航");
  for (const line of readFileSync(index, "utf8").split("\n")) {
    const cells = line
      .trim()
      .replace(/^\||\|$/g, "")
      .split("|")
      .map((c) => c.trim());
    if (!line.trimStart().startsWith("|") || cells.length !== 5) continue;
    const targetLink = links(cells[0])[0] ?? /^`([^`]+)`$/.exec(cells[0])?.[1];
    if (!targetLink) continue;
    const target = resolve(dirname(index), decodeURIComponent(targetLink));
    if (!within(root, target) || (existsSync(target) && !within(root, realpathSync(target))))
      errors.push(`索引引用越出仓库：${targetLink}`);
    else if (!existsSync(target) || !statSync(target).isFile()) errors.push(`索引引用不存在：${targetLink}`);
    if (registrations.has(target)) errors.push(`索引重复登记：${relative(root, target)}`);
    registrations.set(target, cells[4]);
    if (!validDate(cells[4])) errors.push(`复核日期非法：${cells[0]}`);
  }
  const active = markdownFiles(root);
  for (const page of active) {
    const name = relative(root, page).split(sep).join("/");
    if (isManagedDocument(name) && !registrations.has(page)) errors.push(`活动文档未登记：${name}`);
    if (name.startsWith("docs/releases/")) errors.push(...releaseNotesErrors(name, readFileSync(page, "utf8")));
    if (!name.startsWith("docs/plans/") || name === "docs/plans/todo.md") continue;
    const match = /^(\d{8})-[a-z0-9-]+\.md$/.exec(name.split("/").at(-1)!);
    const dates = [...readFileSync(page, "utf8").matchAll(/^最后维护：(\d{4}-\d{2}-\d{2})\s*$/gm)].map((m) => m[1]);
    if (!match || dates.length !== 1) errors.push(`计划文件名或维护日期行非法：${name}`);
    else if (match[1] !== dates[0].replaceAll("-", "") || registrations.get(page) !== dates[0])
      errors.push(`计划三处日期不一致：${name}`);
  }
  errors.push(...linkErrors(root, active));
  const git = run("git", ["-C", root, "rev-parse", "--show-toplevel"], {
    capture: true,
    check: false,
  });
  if (git.status === 0 && realpathSync(git.stdout.trim()) === root) {
    const changes = run("git", ["-C", root, "diff", "HEAD", "--name-only", "-z"], {
      capture: true,
      check: false,
    });
    const others = run("git", ["-C", root, "ls-files", "--others", "--exclude-standard", "-z"], {
      capture: true,
    });
    const names = new Set((changes.stdout + others.stdout).split("\0").filter(Boolean));
    if ([...names].some(isManagedDocument) && !names.has(INDEX)) errors.push(`活动文档变更未同步 ${INDEX}`);
  }
  return errors;
}
if (isMain(import.meta.url))
  await cli(() => report(violations(context().root), "文档索引、计划日期与活动引用检查通过"));
