import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { afterEach, expect, test } from "vitest";
import { syncPrivacyFile } from "../../scripts/sync-kmp-strings.ts";

const tempRoots: string[] = [];

afterEach(() => {
  while (tempRoots.length > 0) {
    rmSync(tempRoots.pop()!, { recursive: true, force: true });
  }
});

function createPrivacyFixture(suffix = "") {
  const root = mkdtempSync(join(tmpdir(), "gazekeep-privacy-"));
  tempRoots.push(root);
  const resourcesDir = join(root, "resources");
  mkdirSync(join(resourcesDir, "values"), { recursive: true });
  mkdirSync(join(resourcesDir, "values-en"), { recursive: true });
  const zh = [`仅本机检测${suffix}`, `零网络零收集${suffix}`, `注视与离开语义${suffix}`];
  const en = [`Local detection${suffix}`, `Zero network${suffix}`, `Gaze semantics${suffix}`];
  const xmlFor = (lines: string[]) =>
    `<resources>\n${lines.map((line, i) => `  <string name="privacy_line_${i + 1}">${line}</string>`).join("\n")}\n</resources>\n`;
  writeFileSync(join(resourcesDir, "values/strings.xml"), xmlFor(zh));
  writeFileSync(join(resourcesDir, "values-en/strings.xml"), xmlFor(en));
  const policyPath = join(root, "product.md");
  writeFileSync(policyPath, `${zh.join("\n")}\n${en.join("\n")}\n`);
  const outDir = join(root, "out");
  mkdirSync(outDir, { recursive: true });
  const outputPath = join(outDir, "privacy.generated.json");
  return {
    outDir,
    outputPath,
    resourcesDir,
    zh,
    en,
    options: {
      resourcesRoot: pathToFileURL(`${resourcesDir}/`),
      policyUrl: pathToFileURL(policyPath),
      outputUrl: pathToFileURL(outputPath),
    },
  };
}

test("隐私同步首次写入合法 JSON，内容未变时跳过覆写且无临时文件残留", async () => {
  const f = createPrivacyFixture();
  const first = await syncPrivacyFile(f.options);
  expect(first).toEqual({ written: true });
  expect(JSON.parse(readFileSync(f.outputPath, "utf8"))).toEqual({ zh: f.zh, en: f.en });
  expect(readdirSync(f.outDir)).toEqual(["privacy.generated.json"]);

  const beforeStat = statSync(f.outputPath);
  const second = await syncPrivacyFile(f.options);
  const afterStat = statSync(f.outputPath);
  expect(second).toEqual({ written: false });
  expect(afterStat.ino).toBe(beforeStat.ino);
  expect(afterStat.mtimeMs).toBe(beforeStat.mtimeMs);
  expect(readdirSync(f.outDir)).toEqual(["privacy.generated.json"]);
});

test("隐私同步支持多任务并发写入而不产生半截 JSON 或临时文件残留", async () => {
  const f = createPrivacyFixture("v1");
  const firstBatch = await Promise.all(Array.from({ length: 8 }, () => syncPrivacyFile(f.options)));
  expect(firstBatch.some((r) => r.written)).toBe(true);
  expect(JSON.parse(readFileSync(f.outputPath, "utf8"))).toEqual({ zh: f.zh, en: f.en });
  expect(readdirSync(f.outDir)).toEqual(["privacy.generated.json"]);

  // 更新真源后再次并发覆盖，验证读端/写端交错后仍保持完整 JSON。
  const nextZh = ["新版仅本机", "新版零网络", "新版语义"];
  const nextEn = ["Next local", "Next offline", "Next semantics"];
  writeFileSync(
    join(f.resourcesDir, "values/strings.xml"),
    `<resources>${nextZh.map((line, i) => `<string name="privacy_line_${i + 1}">${line}</string>`).join("")}</resources>`,
  );
  writeFileSync(
    join(f.resourcesDir, "values-en/strings.xml"),
    `<resources>${nextEn.map((line, i) => `<string name="privacy_line_${i + 1}">${line}</string>`).join("")}</resources>`,
  );
  writeFileSync(new URL(f.options.policyUrl), `${nextZh.join("\n")}\n${nextEn.join("\n")}\n`);

  await Promise.all(Array.from({ length: 8 }, () => syncPrivacyFile(f.options)));
  expect(JSON.parse(readFileSync(f.outputPath, "utf8"))).toEqual({ zh: nextZh, en: nextEn });
  expect(readdirSync(f.outDir)).toEqual(["privacy.generated.json"]);
});
