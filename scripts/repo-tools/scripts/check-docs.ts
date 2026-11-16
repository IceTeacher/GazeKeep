import { existsSync, readFileSync, realpathSync, statSync, writeFileSync } from "node:fs";
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

const RELEASE_SECTIONS = {
  zh: ["更新日志", "启用步骤"],
  en: ["Changelog", "Setup"],
} as const;

export interface ParsedChangelog {
  sections: Map<string, string>;
  errors: string[];
}

export function parseChangelog(name: string, text: string, locale: "zh" | "en"): ParsedChangelog {
  const errors: string[] = [];
  const sections = new Map<string, string>();
  let fenced = false;
  let currentVersion = "";
  let currentLines: string[] = [];

  const flush = () => {
    if (!currentVersion) return;
    const body = currentLines.join("\n").trim();
    sections.set(currentVersion, body);
    let innerFenced = false;
    const h2s: string[] = [];
    for (const line of body.split("\n")) {
      if (/^\s*(```|~~~)/.test(line)) innerFenced = !innerFenced;
      else if (!innerFenced && /^#{1,2} /.test(line)) h2s.push(line.trim());
    }
    const expected = RELEASE_SECTIONS[locale];
    if (h2s.join("\n") !== expected.map((s) => `## ${s}`).join("\n")) {
      errors.push(`${name} (${currentVersion}): 版本章节只能依次包含「## ${expected[0]}」「## ${expected[1]}」两节`);
    }
  };

  for (const line of text.replace(/\r\n?/g, "\n").split("\n")) {
    if (/^\s*(```|~~~)/.test(line)) {
      fenced = !fenced;
      if (currentVersion) currentLines.push(line);
      continue;
    }
    if (!fenced && line.startsWith("# ")) {
      flush();
      const title = line.slice(2).trim();
      if (!/^v\d+\.\d+\.\d+$/.test(title)) {
        errors.push(`${name}: 一级标题必须为 # v<版本>，实际为 ${line}`);
        currentVersion = "";
        currentLines = [];
        continue;
      }
      if (sections.has(title)) {
        errors.push(`${name}: 重复的版本标题 ${title}`);
      }
      currentVersion = title;
      currentLines = [];
      continue;
    }
    if (currentVersion) {
      currentLines.push(line);
    } else if (line.trim()) {
      errors.push(`${name}: 版本标题前存在多余内容：${line}`);
    }
  }
  flush();
  if (sections.size === 0 && errors.length === 0) {
    errors.push(`${name}: 至少需要包含一个 # v<版本> 章节`);
  }
  return { sections, errors };
}

export function extractReleaseNotes(text: string, tag: string): string | undefined {
  const { sections } = parseChangelog("docs/CHANGELOG.md", text, "zh");
  return sections.get(tag);
}

export function violations(root: string) {
  const errors: string[] = [];
  if (!existsSync(join(root, "docs/index.md"))) errors.push("缺少 docs/index.md 读者导航");
  const active = markdownFiles(root);
  errors.push(...linkErrors(root, active));

  const zhPath = join(root, "docs/CHANGELOG.md");
  const enPath = join(root, "docs/CHANGELOG.en.md");
  const hasZh = existsSync(zhPath);
  const hasEn = existsSync(enPath);
  if (hasZh !== hasEn) {
    errors.push(`缺少配对双语更新日志：${hasZh ? "docs/CHANGELOG.en.md" : "docs/CHANGELOG.md"}`);
  } else if (hasZh && hasEn) {
    const zh = parseChangelog("docs/CHANGELOG.md", readFileSync(zhPath, "utf8"), "zh");
    const en = parseChangelog("docs/CHANGELOG.en.md", readFileSync(enPath, "utf8"), "en");
    errors.push(...zh.errors, ...en.errors);
    const zhVersions = [...zh.sections.keys()];
    const enVersions = [...en.sections.keys()];
    if (zhVersions.join(",") !== enVersions.join(",")) {
      errors.push(`中英文 CHANGELOG 版本列表不一致：zh=[${zhVersions.join(", ")}] vs en=[${enVersions.join(", ")}]`);
    }
  }
  return errors;
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({
      "extract-release": { type: "string" },
      output: { type: "string" },
    });
    const errs = violations(root);
    if (values["extract-release"]) {
      const tag = values["extract-release"];
      const zhPath = join(root, "docs/CHANGELOG.md");
      const enPath = join(root, "docs/CHANGELOG.en.md");
      if (!existsSync(zhPath) || !existsSync(enPath)) {
        errs.push("缺少 docs/CHANGELOG.md 或 docs/CHANGELOG.en.md");
      } else {
        const zhNotes = parseChangelog("docs/CHANGELOG.md", readFileSync(zhPath, "utf8"), "zh").sections.get(tag);
        const enNotes = parseChangelog("docs/CHANGELOG.en.md", readFileSync(enPath, "utf8"), "en").sections.get(tag);
        if (!zhNotes) errs.push(`docs/CHANGELOG.md 缺少版本 ${tag}`);
        if (!enNotes) errs.push(`docs/CHANGELOG.en.md 缺少版本 ${tag}`);
        if (zhNotes && values.output) writeFileSync(values.output, `${zhNotes}\n`, "utf8");
        else if (zhNotes && !errs.length) console.log(zhNotes);
      }
    }
    report(errs, "文档导航、链接锚点与双语 CHANGELOG 检查通过");
  });
