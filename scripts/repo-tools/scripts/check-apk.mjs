import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { XMLParser } from "fast-xml-parser";
import { pngPixelHash } from "./png-pixels.mjs";
import { cli, context, isMain, report, run, walk } from "./common.mjs";

const PREFIX = "cn.rainafter.app.gazekeep.xposed.entry.";
const parser = new XMLParser({ ignoreAttributes: false, parseAttributeValue: false });
const list = (value) => (value == null ? [] : Array.isArray(value) ? value : [value]);
const lines = (value) =>
  (value ?? "")
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s && !s.startsWith("#"));

export function resourceTable(dump) {
  const resources = new Map();
  let current;
  for (const line of dump.split(/\r?\n/)) {
    const match = line.match(/^\s*resource (0x[\da-f]+) (\S+)\s*$/i);
    if (match) {
      current = { id: match[1], body: "" };
      resources.set(match[2], current);
    } else if (current) current.body += line + "\n";
  }
  return resources;
}

export function validateApk(snapshot, expected) {
  const errors = [];
  const manifest = parser.parse(snapshot.manifest).manifest;
  const app = manifest?.application;
  if (!app) return ["APK 缺少 application"];
  const resources = resourceTable(snapshot.resources);
  const ref = (name) => (resources.has(name) ? `@ref/${resources.get(name).id}` : undefined);
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
  for (const name of ["ModernEntry", "LegacyEntry"]) {
    if (!definedClasses.includes(PREFIX + name)) errors.push(`DEX 缺少入口 ${name}`);
    if (!snapshot.dex.includes(`${PREFIX}${name} <init>()`)) errors.push(`${name} 缺少无参构造器`);
  }
  for (const name of definedClasses) {
    if (name.startsWith("io.github.libxposed.") || name.startsWith("de.robv.android.xposed."))
      errors.push(`DEX 打包了框架 API：${name}`);
    if (name === "android.app.ActivityThread" || name.startsWith("android.app.ActivityThread$"))
      errors.push(`DEX 打包了隐藏存根：${name}`);
  }
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
  return errors;
}

export function inspectApk(root, apk) {
  if (!existsSync(apk)) throw new Error(`APK 不存在：${apk}`);
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!sdk) throw new Error("verify:apk 需要 ANDROID_HOME 或 ANDROID_SDK_ROOT 与 SDK apkanalyzer");
  const tools = join(sdk, "build-tools");
  const version = readdirSync(tools)
    .filter((name) => /^\d+\.\d+\.\d+$/.test(name) && existsSync(join(tools, name, "aapt2")))
    .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }))[0];
  if (!version) throw new Error("SDK 缺少可用的稳定 build-tools/aapt2");
  const analyze = (args) => run("apkanalyzer", [...args, apk], { capture: true, maxBuffer: 128 * 1024 * 1024 }).stdout;
  const files = lines(analyze(["files", "list"])).map((name) => name.replace(/^\//, ""));
  const entries = {};
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
  const iconXml = {};
  const brandedBitmaps = {};
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
  return {
    manifest: analyze(["manifest", "print"]),
    dex: analyze(["dex", "packages", "--defined-only"]),
    resources,
    entries,
    files,
    iconXml,
    brandedBitmaps,
  };
}

if (isMain(import.meta.url))
  await cli(() => {
    const { root, values } = context({ apk: { type: "string" } });
    if (!values.apk) throw new Error("用法：verify:apk --apk <APK路径> [--root <仓库路径>]");
    const apk = resolve(values.apk);
    const build = readFileSync(join(root, "apps/app/android/build.gradle.kts"), "utf8");
    const description = parser
      .parse(readFileSync(join(root, "apps/app/android/src/main/res/values/strings.xml"), "utf8"))
      .resources.string.find((s) => s["@_name"] === "xposed_description")?.["#text"];
    const expected = {
      versionName: build.match(/versionName\s*=\s*"([^"]+)"/)?.[1],
      versionCode: build.match(/versionCode\s*=\s*(\d+)/)?.[1],
      description,
    };
    if (!expected.versionName || !expected.versionCode || !description) throw new Error("无法读取当前版本与简介配置");
    report(validateApk(inspectApk(root, apk), expected), `APK 门禁通过：${apk}（静态产物检查，不代表实机通过）`);
  });
