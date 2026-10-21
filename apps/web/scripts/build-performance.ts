import { readdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import type { AstroIntegration } from "astro";
// 首屏 shader 组件与页面创建的图层共用同一来源。
import { HERO_SHADER_COMPONENTS } from "../src/scripts/hero-shader.ts";

export { HERO_SHADER_COMPONENTS };

/** 从 shaders 的注册表中删去未用组件；其余定义的导入随包级 sideEffects:false 被摇树移除。 */
export function pruneShaderRegistry(code: string, keep: readonly string[]) {
  const block = /(const shaderDefinitions = \{\n)([\s\S]*?)(\n\t*\};)/.exec(code);
  if (!block) throw new Error("shaders 注册表结构已变化：找不到 shaderDefinitions");
  const entries = block[2]
    .split("\n")
    .map((line) => [/^\s*(\w+):/.exec(line)?.[1] ?? "", line.replace(/,\s*$/, "")] as const);
  const missing = keep.filter((name) => !entries.some(([entry]) => entry === name));
  if (missing.length) throw new Error(`shaders 注册表缺少组件：${missing.join(", ")}`);
  const kept = entries.filter(([name]) => keep.includes(name)).map(([, line]) => line);
  return (
    code.slice(0, block.index) + block[1] + kept.join(",\n") + block[3] + code.slice(block.index + block[0].length)
  );
}

/** Vite 插件：仅对 shaders 包内的注册表模块生效。 */
export function shaderRegistryPlugin(keep: readonly string[] = HERO_SHADER_COMPONENTS) {
  return {
    name: "gazekeep:shader-registry",
    enforce: "pre" as const,
    transform(code: string, id: string) {
      if (!/[\\/]shaders[\\/]dist[\\/]core[\\/]shaderRegistry-[\w-]+\.js$/.test(id)) return;
      return { code: pruneShaderRegistry(code, keep), map: null };
    },
  };
}

/** 在加载模块脚本的页面 head 中预加载动态分块；重复注入与无脚本页面保持原样。 */
export function injectModulePreload(html: string, hrefs: string[]) {
  if (!/<script type="module"/.test(html)) return html;
  const links = hrefs
    .filter((href) => !html.includes(`<link rel="modulepreload" href="${href}">`))
    .map((href) => `<link rel="modulepreload" href="${href}">`)
    .join("");
  return links ? html.replace("</head>", `${links}</head>`) : html;
}

/** Astro 集成：构建后为首帧机身所需的 Three.js 分块加 modulepreload，与入口脚本并行下载。 */
export function preloadFirstFrameChunks(): AstroIntegration {
  return {
    name: "gazekeep:preload-first-frame",
    hooks: {
      "astro:build:done": async ({ dir }) => {
        const root = fileURLToPath(dir);
        const chunks = (await readdir(`${root}/_astro`)).filter((name) => /^three\.module\.[\w-]+\.js$/.test(name));
        if (chunks.length !== 1) throw new Error(`应恰有一个 Three.js 分块，实际：${chunks.join(", ") || "无"}`);
        const files = (await readdir(root, { recursive: true })).filter(
          (name) => name.endsWith(".html") && !name.startsWith("kmp-showcase"),
        );
        for (const file of files) {
          const html = await readFile(`${root}/${file}`, "utf8");
          const next = injectModulePreload(
            html,
            chunks.map((name) => `/_astro/${name}`),
          );
          if (next !== html) await writeFile(`${root}/${file}`, next);
        }
      },
    },
  };
}
