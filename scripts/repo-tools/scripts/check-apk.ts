import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { XMLParser } from "fast-xml-parser";
import { pngPixelHash } from "./png-pixels.ts";
import { cli, context, isMain, report, run, walk } from "./common.ts";

const PREFIX = "cn.rainafter.app.gazekeep.xposed.entry.";
const FAULT_INJECTION = "cn.rainafter.app.gazekeep.backend.shizuku.UserActivityFaultInjection";
const parser = new XMLParser({ ignoreAttributes: false, parseAttributeValue: false });
// fast-xml-parser 的结果没有固定结构，节点按 any 处理，缺失字段由下方断言兜底。
type XmlNode = any;
export interface ApkSnapshot {
  manifest: string;
  dex: string;
  resources: string;
  entries: Partial<Record<string, string>>;
  files: string[];
  iconXml: Record<string, string>;
  brandedBitmaps: Record<string, boolean>;
  /** 原生库路径 → 是否压缩存放。 */
  nativeLibs: Record<string, boolean>;
}
export interface ApkExpectation {
  versionName: string;
  versionCode: string;
  description: string;
  abis: readonly string[];
}
const list = (value: XmlNode): XmlNode[] => (value == null ? [] : Array.isArray(value) ? value : [value]);
const lines = (value: string | undefined) =>
  (value ?? "")
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s && !s.startsWith("#"));

export function resourceTable(dump: string) {
  const resources = new Map<string, { id: string; body: string }>();
  let current: { id: string; body: string } | undefined;
  for (const line of dump.split(/\r?\n/)) {
    const match = line.match(/^\s*resource (0x[\da-f]+) (\S+)\s*$/i);
    if (match) {
      current = { id: match[1], body: "" };
      resources.set(match[2], current);
    } else if (current) current.body += line + "\n";
  }
  return resources;
}

// apkanalyzer 不输出条目的压缩方式，直接读 ZIP 中央目录；APK 远小于 4 GiB，不处理 ZIP64。
export function zipCompression(zip: Buffer) {
  let end = -1;
  for (let at = zip.length - 22; at >= Math.max(0, zip.length - 22 - 0xffff); at--) {
    if (zip.readUInt32LE(at) === 0x06054b50) {
      end = at;
      break;
    }
  }
  if (end < 0) throw new Error("APK 缺少 ZIP 目录尾记录");
  const entries = new Map<string, boolean>();
  let at = zip.readUInt32LE(end + 16);
  for (let index = zip.readUInt16LE(end + 10); index > 0; index--) {
    if (zip.readUInt32LE(at) !== 0x02014b50) throw new Error("APK 中央目录损坏");
    const nameLength = zip.readUInt16LE(at + 28);
    const name = zip.toString("utf8", at + 46, at + 46 + nameLength);
    entries.set(name, zip.readUInt16LE(at + 10) !== 0);
    at += 46 + nameLength + zip.readUInt16LE(at + 30) + zip.readUInt16LE(at + 32);
  }
  return entries;
}

export function validateApk(snapshot: ApkSnapshot, expected: ApkExpectation) {
  const errors: string[] = [];
  const manifest: XmlNode = parser.parse(snapshot.manifest).manifest;
  const app = manifest?.application;
  if (!app) return ["APK 缺少 application"];
  const resources = resourceTable(snapshot.resources);
  const ref = (name: string) => (resources.has(name) ? `@ref/${resources.get(name)!.id}` : undefined);
  const metadata = new Map(list(app["meta-data"]).map((item) => [item["@_android:name"], item]));
  if (
    manifest["@_package"] !== "cn.rainafter.app.gazekeep" ||
    manifest["@_android:versionName"] !== expected.versionName ||
    manifest["@_android:versionCode"] !== expected.versionCode
  )
    errors.push("APK 包名或版本不符合当前配置");
  for (const permission of list(manifest["uses-permission"])) {
    const name = permission["@_android:name"] ?? "";
    if (
      /^android\.permission\.(INTERNET|QUERY_ALL_PACKAGES|SYSTEM_ALERT_WINDOW|READ_PHONE_STATE|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|MANAGE_MEDIA|READ_MEDIA_.*)$/.test(
        name,
      )
    ) {
      errors.push(`APK 含禁止权限：${name}`);
    }
  }
  if (
    metadata.get("xposedmodule")?.["@_android:value"] !== "true" ||
    metadata.get("xposedminversion")?.["@_android:value"] !== "93"
  )
    errors.push("旧式模块声明不正确");
  const descriptionRef = ref("string/xposed_description");
  if (
    !descriptionRef ||
    app["@_android:description"] !== descriptionRef ||
    metadata.get("xposeddescription")?.["@_android:resource"] !== descriptionRef
  )
    errors.push("双入口简介引用不一致");
  if (!resources.get("string/xposed_description")?.body.includes(JSON.stringify(expected.description)))
    errors.push("APK 中文简介不符合当前资源");
  if (
    metadata.get("xposedscope")?.["@_android:resource"] !== ref("array/xposed_scope") ||
    !resources.get("array/xposed_scope")?.body.match(/^\s*\["android"\]\s*$/m)
  )
    errors.push("旧式作用域必须仅为 android");

  if (lines(snapshot.entries["META-INF/xposed/java_init.list"]).join("\n") !== `${PREFIX}ModernEntry`)
    errors.push("现代入口登记缺失或不正确");
  if (lines(snapshot.entries["assets/xposed_init"]).join("\n") !== `${PREFIX}LegacyEntry`)
    errors.push("旧入口登记缺失或不正确");
  if (lines(snapshot.entries["META-INF/xposed/scope.list"]).join("\n") !== "system")
    errors.push("现代作用域必须仅为 system");
  const properties = new Map(
    lines(snapshot.entries["META-INF/xposed/module.prop"]).map((line) => {
      const at = line.indexOf("=");
      return [line.slice(0, at).trim(), line.slice(at + 1).trim()];
    }),
  );
  for (const [key, value] of Object.entries({
    minApiVersion: "102",
    targetApiVersion: "102",
    staticScope: "true",
    autoHotReload: "false",
  })) {
    if (properties.get(key) !== value) errors.push(`现代 ${key} 必须为 ${value}`);
  }
  const definedClasses = [...snapshot.dex.matchAll(/^C d \d+\s+\d+\s+\d+\s+(\S+)\s*$/gm)].map((m) => m[1]);
  const definedMethods = new Set([...snapshot.dex.matchAll(/^M d \d+\s+\d+\s+\d+\s+(.+)$/gm)].map((m) => m[1].trim()));
  for (const name of ["ModernEntry", "LegacyEntry"]) {
    if (!definedClasses.includes(PREFIX + name)) errors.push(`DEX 缺少入口 ${name}`);
    if (!snapshot.dex.includes(`${PREFIX}${name} <init>()`)) errors.push(`${name} 缺少无参构造器`);
  }
  for (const hooker of ["BeforeHooker", "AfterHooker"]) {
    const name = `${PREFIX}ModernHookFacade$${hooker}`;
    if (!definedClasses.includes(name)) errors.push(`DEX 缺少现代拦截器类：${name}`);
    if (!definedMethods.has(`${name} java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain)`))
      errors.push(`现代拦截器缺少完整 intercept 回调：${name}`);
  }
  const registrarPrefix = "com.google.firebase.components:";
  const components = [app, ...list(app.service), ...list(app.provider)];
  for (const component of components) {
    for (const item of list(component["meta-data"])) {
      const key = item["@_android:name"] ?? "";
      if (
        !key.startsWith(registrarPrefix) ||
        item["@_android:value"] !== "com.google.firebase.components.ComponentRegistrar"
      )
        continue;
      const name = key.slice(registrarPrefix.length);
      if (!definedClasses.includes(name)) errors.push(`DEX 缺少组件注册器类：${name}`);
      if (!snapshot.dex.includes(`${name} <init>()`)) errors.push(`组件注册器缺少无参构造器：${name}`);
    }
  }
  for (const name of definedClasses) {
    if (name.startsWith("io.github.libxposed.") || name.startsWith("de.robv.android.xposed."))
      errors.push(`DEX 打包了框架 API：${name}`);
    if (name === "android.app.ActivityThread" || name.startsWith("android.app.ActivityThread$"))
      errors.push(`DEX 打包了隐藏存根：${name}`);
  }
  // 调试故障注入只来自 debug 源码集；不可调试包中出现说明构建类型串入了测试代码。
  if (app["@_android:debuggable"] !== "true" && definedClasses.includes(FAULT_INJECTION))
    errors.push(`不可调试包含调试故障注入：${FAULT_INJECTION}`);
  for (const [attr, name] of [
    ["icon", "mipmap/ic_launcher"],
    ["roundIcon", "mipmap/ic_launcher_round"],
  ]) {
    if (!ref(name) || app[`@_android:${attr}`] !== ref(name)) errors.push(`application ${attr} 未引用品牌图标`);
  }
  for (const name of ["mipmap/ic_launcher", "mipmap/ic_launcher_round", "mipmap/ic_launcher_foreground"]) {
    const files = [...(resources.get(name)?.body ?? "").matchAll(/\(file\) (\S+) type=(PNG|XML)/g)];
    if (!files.length) errors.push(`缺少图标文件：${name}`);
    for (const [, path, type] of files) {
      if (!snapshot.files.includes(path)) errors.push(`缺少图标文件：${path}`);
      if (type === "PNG" && snapshot.brandedBitmaps[path] !== true) errors.push(`非当前品牌图标：${path}`);
      if (type === "XML") {
        const icon = parser.parse(snapshot.iconXml[path] ?? "")["adaptive-icon"];
        if (
          !icon?.background?.["@_android:drawable"] ||
          icon?.foreground?.["@_android:drawable"] !== ref("mipmap/ic_launcher_foreground")
        )
          errors.push(`自适应前景引用不正确：${path}`);
      }
    }
  }
  // 分 ABI 发布时每个包只能含对应 ABI；原生库压缩存放以减小下载体积。
  const abis = new Set(Object.keys(snapshot.nativeLibs).map((path) => path.split("/")[1]));
  for (const abi of expected.abis) if (!abis.has(abi)) errors.push(`缺少 ABI 原生库：${abi}`);
  for (const abi of abis) if (!expected.abis.includes(abi)) errors.push(`APK 含多余 ABI：${abi}`);
  for (const [path, compressed] of Object.entries(snapshot.nativeLibs))
    if (!compressed) errors.push(`原生库未压缩：${path}`);
  return errors;
}

export function inspectApk(root: string, apk: string): ApkSnapshot {
  if (!existsSync(apk)) throw new Error(`APK 不存在：${apk}`);
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!sdk) throw new Error("verify:apk 需要 ANDROID_HOME 或 ANDROID_SDK_ROOT 与 SDK apkanalyzer");
  const tools = join(sdk, "build-tools");
  const version = readdirSync(tools)
    .filter((name) => /^\d+\.\d+\.\d+$/.test(name) && existsSync(join(tools, name, "aapt2")))
    .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }))[0];
  if (!version) throw new Error("SDK 缺少可用的稳定 build-tools/aapt2");
  const analyze = (args: string[]) =>
    run("apkanalyzer", [...args, apk], { capture: true, maxBuffer: 128 * 1024 * 1024 }).stdout;
  const files = lines(analyze(["files", "list"])).map((name) => name.replace(/^\//, ""));
  const entries: Record<string, string> = {};
  for (const name of [
    "META-INF/xposed/java_init.list",
    "META-INF/xposed/module.prop",
    "META-INF/xposed/scope.list",
    "assets/xposed_init",
  ]) {
    if (files.includes(name)) entries[name] = analyze(["files", "cat", "--file", name]);
  }
  const resources = run(join(tools, version, "aapt2"), ["dump", "resources", apk], { capture: true }).stdout;
  const table = resourceTable(resources);
  const hash = pngPixelHash;
  const sourceHashes = new Set(
    walk(join(root, "apps/app/android/src/main/res"))
      .filter((name) => /\/mipmap-[^/]+\/ic_launcher(?:_foreground|_round)?\.png$/.test(name))
      .map((name) => hash(readFileSync(name))),
  );
  const iconXml: Record<string, string> = {};
  const brandedBitmaps: Record<string, boolean> = {};
  for (const name of ["mipmap/ic_launcher", "mipmap/ic_launcher_round", "mipmap/ic_launcher_foreground"]) {
    for (const [, path, type] of (table.get(name)?.body ?? "").matchAll(/\(file\) (\S+) type=(PNG|XML)/g)) {
      if (!files.includes(path)) continue;
      if (type === "XML") iconXml[path] = analyze(["resources", "xml", "--file", path]);
      else {
        const result = spawnSync("apkanalyzer", ["files", "cat", "--file", path, apk], { maxBuffer: 32 * 1024 * 1024 });
        if (result.error) throw result.error;
        if (result.status !== 0) throw new Error(`读取 APK 图标失败：${path}`);
        brandedBitmaps[path] = sourceHashes.has(hash(result.stdout));
      }
    }
  }
  const nativeLibs = Object.fromEntries(
    [...zipCompression(readFileSync(apk))].filter(([name]) => /^lib\/[^/]+\/[^/]+\.so$/.test(name)),
  );
  return {
    manifest: analyze(["manifest", "print"]),
    dex: analyze(["dex", "packages", "--defined-only"]),
    resources,
    entries,
    files,
    iconXml,
    brandedBitmaps,
    nativeLibs,
  };
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({ apk: { type: "string" }, abi: { type: "string", multiple: true } });
    if (!values.apk || !values.abi?.length)
      throw new Error("用法：verify:apk --apk <APK路径> --abi <ABI> [--abi <ABI>...] [--root <仓库路径>]");
    const apk = resolve(values.apk);
    const build = readFileSync(join(root, "apps/app/android/build.gradle.kts"), "utf8");
    const description = parser
      .parse(readFileSync(join(root, "apps/app/android/src/main/res/values/strings.xml"), "utf8"))
      .resources.string.find((s: XmlNode) => s["@_name"] === "xposed_description")?.["#text"];
    const versionName = build.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
    const versionCode = build.match(/versionCode\s*=\s*(\d+)/)?.[1];
    if (!versionName || !versionCode || !description) throw new Error("无法读取当前版本与简介配置");
    report(
      validateApk(inspectApk(root, apk), { versionName, versionCode, description, abis: values.abi }),
      `APK 门禁通过：${apk}（静态产物检查，不代表实机通过）`,
    );
  });
