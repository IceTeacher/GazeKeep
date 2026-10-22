import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";
import { describe, expect, test } from "vitest";

const projectRoot = fileURLToPath(new URL("../../", import.meta.url));
const testsRoot = path.join(projectRoot, "tests");
// tests/<目录> 镜像工程内同名源码目录。
const mirrors = ["src", "scripts"];
const reserved = ["scenarios", "architecture", "fixtures", "_support"];
const sourceExtensions = [".ts", ".astro"];
const vitestConfig = readFileSync(path.join(projectRoot, "vitest.config.ts"), "utf8");

function listFiles(directory: string): string[] {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === "__screenshots__" ? [] : listFiles(target);
    return [target];
  });
}

function trackedFiles(directory: string): string[] {
  const output = execFileSync("git", ["ls-files", "--", directory], { cwd: projectRoot, encoding: "utf8" });
  return output.split("\n").filter(Boolean);
}

const isTest = (file: string) => file.endsWith(".test.ts");
const isBrowserTest = (file: string) => file.endsWith(".browser.test.ts");
const isWithin = (directory: string, target: string) => {
  const relative = path.relative(directory, target);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
};

/** tests/src/a/b.browser.test.ts 依次尝试 src/a/b.browser.{ts,astro} 与 src/a/b.{ts,astro}。 */
function sourceCandidates(file: string): string[] {
  const relative = path.relative(testsRoot, file);
  const stem = relative.replace(/\.test\.ts$/, "");
  const stems = new Set([stem, stem.replace(/\.browser$/, "")]);
  return [...stems].flatMap((candidate) =>
    sourceExtensions.map((extension) => path.join(projectRoot, `${candidate}${extension}`)),
  );
}

/** 由 TypeScript 词法扫描导入，模板字符串里的示例代码不计入。 */
function relativeImports(file: string): string[] {
  const { importedFiles } = ts.preProcessFile(readFileSync(file, "utf8"), true, true);
  return importedFiles.map((entry) => entry.fileName).filter((name) => name.startsWith("."));
}

describe("测试目录布局", () => {
  test("源码目录中不保留已跟踪的测试文件", () => {
    const violations = mirrors.flatMap((directory) => trackedFiles(directory).filter(isTest));
    expect(violations).toEqual([]);
  });

  test("tests 顶层只保留镜像目录与约定保留区", () => {
    const allowed = new Set([...mirrors, ...reserved]);
    const violations = readdirSync(testsRoot, { withFileTypes: true })
      .filter((entry) => !entry.isDirectory() || !allowed.has(entry.name))
      .map((entry) => entry.name);
    expect(violations).toEqual([]);
  });

  test("unit 与 browser 按文件名互斥，且都收集到测试", () => {
    const tests = listFiles(testsRoot).filter(isTest);
    expect(tests.filter(isBrowserTest).length).toBeGreaterThan(0);
    expect(tests.filter((file) => !isBrowserTest(file)).length).toBeGreaterThan(0);
    expect(vitestConfig).toMatch(/name:\s*"unit"[\s\S]*include:\s*\["tests\/\*\*\/\*\.test\.ts"\]/);
    expect(vitestConfig).toMatch(/exclude:\s*\["tests\/\*\*\/\*\.browser\.test\.ts"\]/);
    expect(vitestConfig).toMatch(/name:\s*"browser"[\s\S]*include:\s*\["tests\/\*\*\/\*\.browser\.test\.ts"\]/);
    expect(vitestConfig).not.toContain("passWithNoTests");
  });

  test("镜像测试都能映射到对应源码", () => {
    const violations = mirrors
      .flatMap((directory) => listFiles(path.join(testsRoot, directory)).filter(isTest))
      .filter((file) => !sourceCandidates(file).some((candidate) => existsSync(candidate)))
      .map((file) => path.relative(projectRoot, file));
    expect(violations).toEqual([]);
  });

  test("测试的相对导入只指向 tests 或被测源码目录", () => {
    const roots = [testsRoot, ...mirrors.map((directory) => path.join(projectRoot, directory))];
    const violations = listFiles(testsRoot)
      .filter((file) => file.endsWith(".ts"))
      .flatMap((file) =>
        relativeImports(file).flatMap((specifier) => {
          const resolved = path.resolve(path.dirname(file), specifier);
          if (!existsSync(resolved) || !statSync(resolved).isFile()) {
            return [`${path.relative(projectRoot, file)} -> ${specifier}（无法解析）`];
          }
          return roots.some((root) => isWithin(root, resolved))
            ? []
            : [`${path.relative(projectRoot, file)} -> ${specifier}`];
        }),
      );
    expect(violations).toEqual([]);
  });
});
