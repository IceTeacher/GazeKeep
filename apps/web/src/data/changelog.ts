import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Locale } from "./schema.ts";

export type ChangeKind = "added" | "improved" | "fixed" | "signing" | "other";

export interface ChangeGroup {
  kind: ChangeKind;
  title: string;
  html: string;
}

export interface ReleaseEntry {
  version: string;
  locale: Locale;
  isFallback: boolean;
  groups: ChangeGroup[];
  introHtml: string;
  setupHtml: string;
}

export function parseSemver(version: string): [number, number, number] {
  const parts = version.replace(/^v/, "").split(".").map(Number);
  return [parts[0] || 0, parts[1] || 0, parts[2] || 0];
}

export function compareVersionsDesc(a: string, b: string) {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pb[i] - pa[i];
  }
  return 0;
}

function escapeHtml(text: string) {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function sanitizeHref(raw: string) {
  const trimmed = raw.trim();
  if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith("/")) return escapeHtml(trimmed);
  return "#";
}

export function renderInlineMarkdown(text: string): string {
  const tokens: string[] = [];
  const stash = (html: string) => {
    const id = `\uE000${tokens.length}\uE001`;
    tokens.push(html);
    return id;
  };
  let s = text.replace(/`([^`]+)`/g, (_, code: string) => stash(`<code>${escapeHtml(code)}</code>`));
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label: string, url: string) =>
    stash(`<a href="${sanitizeHref(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`),
  );
  s = escapeHtml(s);
  s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  s = s.replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, "<em>$1</em>");
  return s.replace(/\uE000(\d+)\uE001/g, (_, i: string) => tokens[Number(i)] ?? "");
}

function splitTableRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  return trimmed.split("|").map((cell) => cell.trim());
}

function parseTableAlignments(line: string): ("left" | "center" | "right" | "")[] | null {
  const trimmed = line.trim();
  if (!trimmed.includes("|")) return null;
  const cells = splitTableRow(trimmed);
  if (cells.length === 0 || cells.some((c) => !/^:?-{3,}:?$/.test(c))) return null;
  return cells.map((c) => {
    if (c.startsWith(":") && c.endsWith(":")) return "center";
    if (c.endsWith(":")) return "right";
    if (c.startsWith(":")) return "left";
    return "";
  });
}

function renderList(lines: string[], startIndex: number, baseIndent: number, ordered: boolean): [string, number] {
  const tag = ordered ? "ol" : "ul";
  const items: string[] = [];
  let i = startIndex;

  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) {
      let peek = i + 1;
      while (peek < lines.length && !lines[peek].trim()) peek++;
      if (peek >= lines.length) break;
      const next = lines[peek];
      const nextMatch = ordered ? next.match(/^(\s*)\d+\.\s+(.+)$/) : next.match(/^(\s*)-\s+(.+)$/);
      if (nextMatch && nextMatch[1].length >= baseIndent) {
        i = peek;
        continue;
      }
      break;
    }

    const match = ordered ? line.match(/^(\s*)\d+\.\s+(.+)$/) : line.match(/^(\s*)-\s+(.+)$/);
    if (!match || match[1].length < baseIndent) break;
    if (match[1].length > baseIndent) {
      const [nestedHtml, nextIndex] = renderList(lines, i, match[1].length, ordered);
      if (items.length > 0) {
        items[items.length - 1] = items[items.length - 1].replace(/<\/li>$/, `${nestedHtml}</li>`);
      } else {
        items.push(`<li>${nestedHtml}</li>`);
      }
      i = nextIndex;
      continue;
    }

    const bodyParts = [renderInlineMarkdown(match[2])];
    i++;
    while (i < lines.length) {
      const next = lines[i];
      if (!next.trim()) break;
      const nestedUnordered = next.match(/^(\s*)-\s+(.+)$/);
      if (nestedUnordered) {
        if (nestedUnordered[1].length > baseIndent) {
          const [nestedHtml, nextIndex] = renderList(lines, i, nestedUnordered[1].length, false);
          bodyParts.push(nestedHtml);
          i = nextIndex;
          continue;
        }
        break;
      }
      const nestedOrdered = next.match(/^(\s*)\d+\.\s+(.+)$/);
      if (nestedOrdered) {
        if (nestedOrdered[1].length > baseIndent) {
          const [nestedHtml, nextIndex] = renderList(lines, i, nestedOrdered[1].length, true);
          bodyParts.push(nestedHtml);
          i = nextIndex;
          continue;
        }
        break;
      }
      if (/^\s{2,}\S/.test(next)) {
        bodyParts.push(renderInlineMarkdown(next.trim()));
        i++;
        continue;
      }
      break;
    }
    items.push(`<li>${bodyParts.join("")}</li>`);
  }

  return [`<${tag}>${items.join("")}</${tag}>`, i];
}

export function renderMarkdownToHtml(markdown: string): string {
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  const blocks: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) {
      i++;
      continue;
    }

    if (trimmed.startsWith("```")) {
      const codeLines: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) {
        codeLines.push(escapeHtml(lines[i]));
        i++;
      }
      i++;
      blocks.push(`<pre><code>${codeLines.join("\n")}</code></pre>`);
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const level = Math.min(6, Math.max(4, headingMatch[1].length + 1));
      blocks.push(`<h${level}>${renderInlineMarkdown(headingMatch[2])}</h${level}>`);
      i++;
      continue;
    }

    if (trimmed.includes("|") && i + 1 < lines.length) {
      const aligns = parseTableAlignments(lines[i + 1]);
      if (aligns) {
        const headers = splitTableRow(trimmed);
        i += 2;
        const rows: string[][] = [];
        while (i < lines.length && lines[i].trim().includes("|")) {
          rows.push(splitTableRow(lines[i]));
          i++;
        }
        const alignAttr = (idx: number) => (aligns[idx] ? ` style="text-align:${aligns[idx]}"` : "");
        const thead = `<thead><tr>${headers
          .map((cell, idx) => `<th${alignAttr(idx)}>${renderInlineMarkdown(cell)}</th>`)
          .join("")}</tr></thead>`;
        const tbody = rows.length
          ? `<tbody>${rows
              .map(
                (row) =>
                  `<tr>${headers
                    .map((_, idx) => `<td${alignAttr(idx)}>${renderInlineMarkdown(row[idx] ?? "")}</td>`)
                    .join("")}</tr>`,
              )
              .join("")}</tbody>`
          : "";
        blocks.push(`<div class="table-wrap"><table>${thead}${tbody}</table></div>`);
        continue;
      }
    }

    if (trimmed.startsWith(">")) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith(">")) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ""));
        i++;
      }
      blocks.push(`<blockquote>${renderMarkdownToHtml(quoteLines.join("\n"))}</blockquote>`);
      continue;
    }

    const unorderedMatch = line.match(/^(\s*)-\s+(.+)$/);
    if (unorderedMatch) {
      const [listHtml, nextIndex] = renderList(lines, i, unorderedMatch[1].length, false);
      blocks.push(listHtml);
      i = nextIndex;
      continue;
    }

    const orderedMatch = line.match(/^(\s*)\d+\.\s+(.+)$/);
    if (orderedMatch) {
      const [listHtml, nextIndex] = renderList(lines, i, orderedMatch[1].length, true);
      blocks.push(listHtml);
      i = nextIndex;
      continue;
    }

    const paraLines: string[] = [];
    while (i < lines.length) {
      const cur = lines[i];
      const curTrim = cur.trim();
      if (
        !curTrim ||
        curTrim.startsWith("```") ||
        curTrim.startsWith(">") ||
        /^#{1,6}\s+/.test(curTrim) ||
        /^\s*-\s+/.test(cur) ||
        /^\s*\d+\.\s+/.test(cur) ||
        (curTrim.includes("|") && i + 1 < lines.length && parseTableAlignments(lines[i + 1]))
      ) {
        break;
      }
      paraLines.push(curTrim);
      i++;
    }
    blocks.push(`<p>${renderInlineMarkdown(paraLines.join(" "))}</p>`);
  }

  return blocks.join("");
}

export function classifyChangeGroup(title: string): ChangeKind {
  const lower = title.trim().toLowerCase();
  if (lower.includes("新增") || lower.includes("added")) return "added";
  if (lower.includes("优化") || lower.includes("improved") || lower.includes("changed")) return "improved";
  if (lower.includes("修复") || lower.includes("fixed")) return "fixed";
  if (lower.includes("签名") || lower.includes("signing")) return "signing";
  return "other";
}

export function parseReleaseMarkdown(
  version: string,
  locale: Locale,
  markdown: string,
  isFallback = false,
): ReleaseEntry {
  const normalized = markdown.replace(/\r\n?/g, "\n");
  const lines = normalized.split("\n");
  const h2Sections = new Map<string, string[]>();
  let currentH2 = "";

  for (const line of lines) {
    const h2 = line.match(/^##\s+(.+)$/);
    if (h2) {
      currentH2 = h2[1].trim();
      if (!h2Sections.has(currentH2)) h2Sections.set(currentH2, []);
      continue;
    }
    if (currentH2) h2Sections.get(currentH2)!.push(line);
  }

  const changelogLines = h2Sections.get("更新日志") ?? h2Sections.get("Changelog") ?? [];
  const setupLines = h2Sections.get("启用步骤") ?? h2Sections.get("Setup") ?? [];

  const introLines: string[] = [];
  const rawGroups: { title: string; lines: string[] }[] = [];
  let currentH3: { title: string; lines: string[] } | undefined;

  for (const line of changelogLines) {
    const h3 = line.match(/^###\s+(.+)$/);
    if (h3) {
      currentH3 = { title: h3[1].trim(), lines: [] };
      rawGroups.push(currentH3);
      continue;
    }
    if (currentH3) currentH3.lines.push(line);
    else introLines.push(line);
  }

  return {
    version,
    locale,
    isFallback,
    introHtml: renderMarkdownToHtml(introLines.join("\n")),
    groups: rawGroups.map((group) => ({
      kind: classifyChangeGroup(group.title),
      title: group.title,
      html: renderMarkdownToHtml(group.lines.join("\n")),
    })),
    setupHtml: renderMarkdownToHtml(setupLines.join("\n")),
  };
}

export function splitChangelogVersions(markdown: string): Map<string, string> {
  const sections = new Map<string, string>();
  let fenced = false;
  let currentVersion = "";
  let currentLines: string[] = [];

  for (const line of markdown.replace(/\r\n?/g, "\n").split("\n")) {
    if (/^\s*(```|~~~)/.test(line)) {
      fenced = !fenced;
      if (currentVersion) currentLines.push(line);
      continue;
    }
    const h1 = !fenced ? line.match(/^#\s+(v\d+\.\d+\.\d+)\s*$/) : null;
    if (h1) {
      if (currentVersion) sections.set(currentVersion, currentLines.join("\n").trim());
      currentVersion = h1[1];
      currentLines = [];
      continue;
    }
    if (currentVersion) currentLines.push(line);
  }
  if (currentVersion) sections.set(currentVersion, currentLines.join("\n").trim());
  return sections;
}

export function findReleasesDir(startDir: string): string | undefined {
  let current = path.resolve(startDir);
  while (true) {
    const candidate = path.join(current, "docs");
    if (fs.existsSync(path.join(candidate, "CHANGELOG.md"))) return candidate;
    const parent = path.dirname(current);
    if (parent === current) return undefined;
    current = parent;
  }
}

function defaultReleasesDir() {
  const fromModule = findReleasesDir(path.dirname(fileURLToPath(import.meta.url)));
  if (fromModule) return fromModule;
  const fromCwd = findReleasesDir(process.cwd());
  if (fromCwd) return fromCwd;
  return path.resolve(process.cwd(), "docs");
}

export function loadChangelog(locale: Locale, releasesDir = defaultReleasesDir()): ReleaseEntry[] {
  const zhPath = path.join(releasesDir, "CHANGELOG.md");
  if (!fs.existsSync(zhPath)) return [];
  const enPath = path.join(releasesDir, "CHANGELOG.en.md");
  const zhSections = splitChangelogVersions(fs.readFileSync(zhPath, "utf8"));
  const enSections = fs.existsSync(enPath)
    ? splitChangelogVersions(fs.readFileSync(enPath, "utf8"))
    : new Map<string, string>();
  const versions = [...zhSections.keys()].sort((a, b) => compareVersionsDesc(a, b));

  return versions.map((version) => {
    const enMarkdown = enSections.get(version);
    const zhMarkdown = zhSections.get(version) ?? "";
    const hasEn = Boolean(enMarkdown);
    const markdown = locale === "en" && hasEn ? enMarkdown! : zhMarkdown;
    return parseReleaseMarkdown(version, locale, markdown, locale === "en" && !hasEn);
  });
}
