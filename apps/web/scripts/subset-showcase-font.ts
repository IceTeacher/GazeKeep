/** 从 Noto Sans CJK Regular 字体集更新官网展示字体：pnpm --filter web font:showcase <NotoSansCJK-Regular.ttc> */
import { readFile, readdir, writeFile } from "node:fs/promises";
import { renameFont, subsetFace } from "./showcase-font.ts";

const source = process.argv[2];
if (!source) throw new Error("用法：subset-showcase-font.ts <NotoSansCJK-Regular.ttc>");
const shared = new URL("../../app/shared/src/", import.meta.url);
const resources = new URL("commonMain/composeResources/", shared);
let text = "";
for (const folder of (await readdir(resources)).filter((name) => name.startsWith("values")).sort())
  text += await readFile(new URL(`${folder}/strings.xml`, resources), "utf8").catch(() => "");
text += String.fromCharCode(...Array.from({ length: 95 }, (_, i) => 32 + i)) + "ReaderBrowserDocumentsDemoGazeKeep—…";
// 字体集第 3 个字体为简体中文（SC）。
const subset = await subsetFace(await readFile(source), 2, text);
const family = "GazeKeep Showcase Sans";
await writeFile(
  new URL("showcaseMain/composeResources/files/showcase_cjk.otf", shared),
  renameFont(subset, { 1: family, 4: family, 6: "GazeKeepShowcaseSans", 16: family }),
);
