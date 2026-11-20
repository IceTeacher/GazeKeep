import path from "node:path";
import { describe, expect, test } from "vitest";
import {
  classifyChangeGroup,
  compareVersionsDesc,
  findReleasesDir,
  loadChangelog,
  parseReleaseMarkdown,
  renderMarkdownToHtml,
  splitChangelogVersions,
} from "../../../src/data/changelog.ts";

describe("changelog", () => {
  test("版本号按语义降序排列", () => {
    const sorted = ["v1.1.2", "v1.1.6", "v1.1.5", "v1.2.0", "v1.1.3"].sort(compareVersionsDesc);
    expect(sorted).toEqual(["v1.2.0", "v1.1.6", "v1.1.5", "v1.1.3", "v1.1.2"]);
  });

  test("分类标签识别中英文更新类型", () => {
    expect(classifyChangeGroup("新增")).toBe("added");
    expect(classifyChangeGroup("Added")).toBe("added");
    expect(classifyChangeGroup("优化")).toBe("improved");
    expect(classifyChangeGroup("Improved")).toBe("improved");
    expect(classifyChangeGroup("修复")).toBe("fixed");
    expect(classifyChangeGroup("Fixed")).toBe("fixed");
    expect(classifyChangeGroup("签名变更")).toBe("signing");
    expect(classifyChangeGroup("Signing change")).toBe("signing");
  });

  test("Markdown 渲染支持嵌套列表、表格并转义 HTML", () => {
    const md = [
      "### 对比",
      "",
      "- 一级条目：包含 `code` 与 **强调**",
      "  - 二级子项 <script>alert(1)</script>",
      "",
      "| 方案 | 耗时 |",
      "| :--- | ---: |",
      "| LSPosed | 0 ms |",
    ].join("\n");
    const html = renderMarkdownToHtml(md);
    expect(html).toContain("<code>code</code>");
    expect(html).toContain("<strong>强调</strong>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
    expect(html).toContain("<ul><li>一级条目");
    expect(html).toContain("<ul><li>二级子项");
    expect(html).toContain('<div class="table-wrap"><table>');
    expect(html).toContain('style="text-align:right"');
  });

  test("解析单篇与聚合中英文发布说明结构", () => {
    const sections = splitChangelogVersions(
      [
        "# v1.1.6",
        "## Changelog",
        "",
        "### Added",
        "",
        "- Quick Settings tile",
        "",
        "## Setup",
        "",
        "- Enable backend",
      ].join("\n"),
    );
    expect(sections.has("v1.1.6")).toBe(true);
    const entry = parseReleaseMarkdown("v1.1.6", "en", sections.get("v1.1.6")!);
    expect(entry.groups).toHaveLength(1);
    expect(entry.groups[0].kind).toBe("added");
    expect(entry.groups[0].html).toContain("Quick Settings tile");
    expect(entry.setupHtml).toContain("Enable backend");
  });

  test("中英文全量加载全部已发布版本并包含表格与启用步骤", () => {
    const zh = loadChangelog("zh");
    const en = loadChangelog("en");
    expect(zh.map((r) => r.version)).toEqual(["v1.1.7", "v1.1.6", "v1.1.5", "v1.1.4", "v1.1.3", "v1.1.2"]);
    expect(en.map((r) => r.version)).toEqual(zh.map((r) => r.version));
    expect(en.every((r) => !r.isFallback)).toBe(true);
    for (const entry of [...zh, ...en]) {
      expect(entry.groups.length).toBeGreaterThan(0);
      expect(entry.setupHtml).not.toBe("");
    }
    const v117 = zh.find((r) => r.version === "v1.1.7")!;
    expect(v117.groups.some((g) => g.html.includes("<table>"))).toBe(true);
    const v115 = zh.find((r) => r.version === "v1.1.5")!;
    expect(v115.groups.some((g) => g.html.includes("<table>"))).toBe(true);
    const v116 = en.find((r) => r.version === "v1.1.6")!;
    expect(v116.setupHtml).toContain("<table>");

    const fromDist = findReleasesDir(path.resolve(process.cwd(), "dist/chunks"));
    expect(fromDist).toBeTruthy();
    expect(loadChangelog("zh", fromDist!).map((r) => r.version)).toEqual([
      "v1.1.7",
      "v1.1.6",
      "v1.1.5",
      "v1.1.4",
      "v1.1.3",
      "v1.1.2",
    ]);
  });
});
