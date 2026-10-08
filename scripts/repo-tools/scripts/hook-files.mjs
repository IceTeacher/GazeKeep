import { existsSync, lstatSync, readFileSync, realpathSync } from "node:fs";
import { extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { parse } from "smol-toml";
import { cli, context, IGNORED, run, within } from "./common.mjs";

await cli(() => {
  const { root, android, values, positionals } = context({ check: { type: "boolean", default: false } }, true);
  const [kind, ...provided] = positionals;
  if (!["all", "kotlin", "text", "toml"].includes(kind))
    throw new Error("用法：hook-files.mjs all|kotlin|text|toml [--check] [文件…]");
  const names =
    kind === "all" && !provided.length
      ? run("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
          cwd: root,
          capture: true,
        })
          .stdout.split("\0")
          .filter(Boolean)
      : provided;
  const groups = new Map();
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
      : extension === ".toml"
        ? "toml"
        : [".md", ".json", ".yaml", ".yml", ".js", ".cjs", ".mjs", ".ts", ".tsx", ".jsx", ".xml"].includes(extension)
          ? "text"
          : undefined;
    if (actual && (kind === "all" || actual === kind)) {
      if (!groups.has(actual)) groups.set(actual, []);
      groups.get(actual).push(name);
    }
  }
  for (const [actual, files] of groups) {
    if (actual === "kotlin") {
      const version = parse(readFileSync(join(android, "gradle/libs.versions.toml"), "utf8")).versions.ktlint;
      const jar = join(root, ".cache/dev-tools", `ktlint-${version}.jar`);
      if (!existsSync(jar)) throw new Error("ktlint 未准备，请运行 pnpm tools:setup");
      run(
        "java",
        ["-jar", jar, "--editorconfig", join(root, ".editorconfig"), ...(values.check ? [] : ["--format"]), ...files],
        { cwd: root },
      );
    } else if (actual === "text") {
      for (const file of files)
        if ([".js", ".cjs", ".mjs"].includes(extname(file))) run(process.execPath, ["--check", file], { cwd: root });
      run(
        join(root, "node_modules/.bin/prettier"),
        [
          "--config",
          join(root, "scripts/repo-tools/config/prettier.json"),
          "--ignore-path",
          join(root, ".prettierignore"),
          values.check ? "--check" : "--write",
          "--",
          ...files,
        ],
        { cwd: root },
      );
    } else for (const file of files) parse(readFileSync(join(root, file), "utf8"));
  }
});
