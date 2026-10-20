import { existsSync, lstatSync, readFileSync, realpathSync } from "node:fs";
import { extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { XMLValidator } from "fast-xml-parser";
import { parse } from "smol-toml";
import { cli, context, IGNORED, ktlintVersion, run, within } from "./common.ts";

// oxlint 检查脚本类文件；oxfmt 负责其余可格式化文本。XML 只校验语法，保持 strict 空白语义不重排。
const LINTED = [".js", ".cjs", ".mjs", ".ts", ".mts", ".cts", ".tsx", ".jsx", ".astro"];
const FORMATTED = [...LINTED, ".md", ".json", ".jsonc", ".yaml", ".yml", ".css", ".html"];

await cli(() => {
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
  for (const [actual, files] of groups) {
    if (actual === "kotlin") {
      const version = ktlintVersion(android);
      const jar = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
      if (!existsSync(jar)) throw new Error("ktlint 未准备，请运行 pnpm tools:setup");
      run(
        "java",
        ["-jar", jar, "--editorconfig", join(root, ".editorconfig"), ...(values.check ? [] : ["--format"]), ...files],
        { cwd: root },
      );
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
        [
          "--config",
          join(root, ".oxfmtrc.json"),
          "--no-error-on-unmatched-pattern",
          values.check ? "--check" : "--write",
          "--",
          ...files,
        ],
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
