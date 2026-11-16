import { createHash } from "node:crypto";

/** 根据内容生成带 8 位指纹的文件名，供 CDN 长期强缓存并在构建变化时自动失效。 */
export function hashedAssetName(filename: string, content: string | Buffer): string {
  const hash = createHash("sha256").update(content).digest("hex").slice(0, 8);
  const dot = filename.lastIndexOf(".");
  if (dot <= 0) return `${filename}.${hash}`;
  return `${filename.slice(0, dot)}.${hash}${filename.slice(dot)}`;
}

/** 只预加载被入口脚本实际引用的 .wasm，避免把 JS 目标里未被引用的 skiko.wasm 冗余副本推入首帧队列。 */
export function findReferencedWasm(entryCode: string, files: readonly string[]): string[] {
  const fileSet = new Set(files.filter((name) => name.endsWith(".wasm")));
  const webpackAssets = new Set<string>();
  for (const match of entryCode.matchAll(/\.p\s*\+\s*["']([^"']+\.wasm)["']/g)) {
    if (fileSet.has(match[1])) webpackAssets.add(match[1]);
  }
  if (webpackAssets.size > 0) return [...webpackAssets].sort();
  return [...fileSet].filter((name) => entryCode.includes(name)).sort();
}

export interface ShowcasePreloadOptions {
  entry: string;
  wasmFiles: readonly string[];
  hasFont: boolean;
  hasStrings: boolean;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function hasExternalModuleReference(entryCode: string, filename: string): boolean {
  const escaped = escapeRegExp(filename);
  return new RegExp(`(?:import\\s*\\(|new\\s+URL\\(|from\\s+|\\.p\\s*\\+\\s*)["'](?:\\.\\/)?${escaped}["']`).test(
    entryCode,
  );
}

/**
 * 过滤展示页无需分发到 CDN 的构建产物：
 * 剔除 source map、js 冗余的未引用 skiko.wasm/skikod8.mjs/内联化 mjs，以及 Showcase 裁剪掉的 About 图标。
 */
export function shouldCopyShowcaseAsset(
  relativePath: string,
  entryCode: string,
  referencedWasm: readonly string[],
): boolean {
  const normalized = relativePath.replaceAll("\\", "/").replace(/^\.\//, "");
  if (!normalized || normalized.endsWith(".map")) return false;
  if (normalized === "composeResources/gazekeep.shared.generated.resources/drawable/app_icon.png") return false;
  if (normalized.endsWith(".wasm")) return referencedWasm.includes(normalized);
  if (normalized === "skikod8.mjs") return false;
  if (normalized === "skiko.mjs" || normalized === "js-reexport-symbols.mjs") {
    return hasExternalModuleReference(entryCode, normalized);
  }
  return true;
}

/** 生成展示页 iframe 所需的并行预加载标签，将原本串行的入口、WASM、字体与词条表折叠到同一轮网络往返。 */
export function buildShowcasePreloads({ entry, wasmFiles, hasFont, hasStrings }: ShowcasePreloadOptions): string {
  const links = [
    `<link rel="modulepreload" href="./${entry}" />`,
    ...wasmFiles.map((file) => `<link rel="preload" as="fetch" crossorigin="anonymous" href="./${file}" />`),
  ];
  if (hasFont) {
    links.push(
      `<link rel="preload" as="fetch" crossorigin="anonymous" href="./composeResources/gazekeep.shared.generated.resources/files/showcase_cjk.otf" />`,
    );
  }
  if (hasStrings) {
    // 词条表按语言目录分离，按父页或 URL 中的 lang 只预加载当前访问语言对应的 .cvr，避免闲置资源告警。
    links.push(
      `<script>(function(){var l="zh";try{l=new URLSearchParams(location.search).get("lang")||(window.parent&&window.parent.document&&window.parent.document.documentElement.lang)||navigator.language||"zh"}catch(e){}var k=document.createElement("link");k.rel="preload";k.as="fetch";k.crossOrigin="anonymous";k.href="./composeResources/gazekeep.shared.generated.resources/"+(String(l).toLowerCase().startsWith("en")?"values-en":"values")+"/strings.commonMain.cvr";document.head.appendChild(k)})();</script>`,
    );
  }
  return links.join("\n    ");
}

/** 将预加载清单与模块入口脚本注入 showcaseMain 模板。 */
export function renderShowcaseIndex(template: string, preloads: string, entry: string): string {
  if (!template.includes("<!--SHOWCASE_PRELOAD-->")) {
    throw new Error("showcaseMain 模板缺少 <!--SHOWCASE_PRELOAD--> 占位符");
  }
  if (!template.includes("<!--SHOWCASE_ENTRY-->")) {
    throw new Error("showcaseMain 模板缺少 <!--SHOWCASE_ENTRY--> 占位符");
  }
  return template
    .replace("<!--SHOWCASE_PRELOAD-->", preloads)
    .replace("<!--SHOWCASE_ENTRY-->", `<script type="module" src="./${entry}"></script>`);
}
