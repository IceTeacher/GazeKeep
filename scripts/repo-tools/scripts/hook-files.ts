import { existsSync, lstatSync, readFileSync, realpathSync } from "node:fs";
import { extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { XMLValidator } from "fast-xml-parser";
import { parse } from "smol-toml";
import { cli, context, IGNORED, ktlintVersion, run, runAsync, within } from "./common.ts";

// oxlint 检查脚本类文件；oxfmt 负责其余可格式化文本。XML 只校验语法，保持 strict 空白语义不重排。
const LINTED = [".js", ".cjs", ".mjs", ".ts", ".mts", ".cts", ".tsx", ".jsx", ".astro"];
const FORMATTED = [...LINTED, ".md", ".json", ".jsonc", ".yaml", ".yml", ".css", ".html"];

await cli(async () => {
  const { root, android, values, positionals } = context({ check: { type: "boolean", default: false } }, true);
  const [kind, ...provided] = positionals;
  if (!["all", "kotlin", "text", "toml", "xml"].includes(kind))
    throw new Error("用法：hook-files.ts all|kotlin|text|toml|xml [--check] [文件…]");
  const names =
    kind === "all" && !provided.length
      ? run("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
          cwd: root,
          capture: true,
        })
          .stdout.split("\0")
          .filter(Boolean)
      : provided;
  const groups = new Map<string, string[]>();
  for (const name of [...new Set(names)].sort()) {
    if (isAbsolute(name) || name.split(/[\\/]/).includes("..")) throw new Error(`文件必须是仓库内相对路径：${name}`);
    const path = resolve(root, name);
    if (!existsSync(path) || lstatSync(path).isSymbolicLink() || !lstatSync(path).isFile()) continue;
    if (!within(root, realpathSync(path))) throw new Error(`文件越出仓库：${name}`);
    if (
      relative(root, path)
        .split(sep)
        .some((p) => IGNORED.has(p)) ||
      name === "pnpm-lock.yaml"
    )
      continue;
    const extension = extname(name);
    const actual = [".kt", ".kts"].includes(extension)
      ? "kotlin"
      : [".toml", ".xml"].includes(extension)
        ? extension.slice(1)
        : FORMATTED.includes(extension)
          ? "text"
          : undefined;
    if (actual && (kind === "all" || actual === kind)) {
      if (!groups.has(actual)) groups.set(actual, []);
      groups.get(actual)!.push(name);
    }
  }

  const runCheckTask = async (command: string, args: string[]) => {
    const result = await runAsync(command, args, { cwd: root, capture: true, check: false });
    if (result.stdout) process.stdout.write(result.stdout);
    if (result.stderr) process.stderr.write(result.stderr);
    if (result.status !== 0) throw new Error(`${command} 检查失败（${result.status ?? result.signal}）`);
  };

  if (values.check) {
    // 只读检查模式：各工具互不修改工作区，并行执行以缩短全仓校验耗时。
    const tasks: Promise<void>[] = [];
    for (const [actual, files] of groups) {
      if (actual === "kotlin") {
        const version = ktlintVersion(android);
        const jar = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
        if (!existsSync(jar)) throw new Error("ktlint 未准备，请运行 pnpm tools:setup");
        tasks.push(runCheckTask("java", ["-jar", jar, "--editorconfig", join(root, ".editorconfig"), ...files]));
      } else if (actual === "text") {
        const linted = files.filter((file) => LINTED.includes(extname(file)));
        if (linted.length) {
          tasks.push(
            runCheckTask(join(root, "node_modules/.bin/oxlint"), [
              "--config",
              join(root, ".oxlintrc.json"),
              "--deny-warnings",
              "--",
              ...linted,
            ]),
          );
        }
        tasks.push(
          runCheckTask(join(root, "node_modules/.bin/oxfmt"), [
            "--config",
            join(root, ".oxfmtrc.json"),
            "--no-error-on-unmatched-pattern",
            "--check",
            "--",
            ...files,
          ]),
        );
      } else if (actual === "xml") {
        tasks.push(
          Promise.resolve().then(() => {
            for (const file of files) {
              const result = XMLValidator.validate(readFileSync(join(root, file), "utf8"));
              if (result !== true) throw new Error(`${file}:${result.err.line}: XML 非法：${result.err.msg}`);
            }
          }),
        );
      } else {
        tasks.push(
          Promise.resolve().then(() => {
            for (const file of files) parse(readFileSync(join(root, file), "utf8"));
          }),
        );
      }
    }
    const settled = await Promise.allSettled(tasks);
    const failures = settled.filter((r): r is PromiseRejectedResult => r.status === "rejected");
    if (failures.length) {
      throw new Error(
        failures.map((f) => (f.reason instanceof Error ? f.reason.message : String(f.reason))).join("\n"),
      );
    }
    return;
  }

  for (const [actual, files] of groups) {
    if (actual === "kotlin") {
      const version = ktlintVersion(android);
      const jar = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
      if (!existsSync(jar)) throw new Error("ktlint 未准备，请运行 pnpm tools:setup");
      run("java", ["-jar", jar, "--editorconfig", join(root, ".editorconfig"), "--format", ...files], { cwd: root });
    } else if (actual === "text") {
      const linted = files.filter((file) => LINTED.includes(extname(file)));
      if (linted.length)
        run(
          join(root, "node_modules/.bin/oxlint"),
          ["--config", join(root, ".oxlintrc.json"), "--deny-warnings", "--", ...linted],
          { cwd: root },
        );
      run(
        join(root, "node_modules/.bin/oxfmt"),
        ["--config", join(root, ".oxfmtrc.json"), "--no-error-on-unmatched-pattern", "--write", "--", ...files],
        { cwd: root },
      );
    } else if (actual === "xml")
      for (const file of files) {
        const result = XMLValidator.validate(readFileSync(join(root, file), "utf8"));
        if (result !== true) throw new Error(`${file}:${result.err.line}: XML 非法：${result.err.msg}`);
      }
    else for (const file of files) parse(readFileSync(join(root, file), "utf8"));
  }
});
