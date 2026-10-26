import assert from "node:assert/strict";
import test from "node:test";
import { type ApkSnapshot, validateApk, zipCompression } from "../scripts/check-apk.ts";

const PREFIX = "cn.rainafter.app.gazekeep.xposed.entry.";
const description = "注视屏幕时延后熄屏；看不清或离开时按系统超时熄屏。";
function fixture(): ApkSnapshot {
  return {
    manifest: `<manifest package="cn.rainafter.app.gazekeep" xmlns:android="http://schemas.android.com/apk/res/android" android:versionName="1.1.0" android:versionCode="2"><application android:icon="@ref/0x7f010001" android:roundIcon="@ref/0x7f010003" android:description="@ref/0x7f020001"><meta-data android:name="xposedmodule" android:value="true"/><meta-data android:name="xposedminversion" android:value="93"/><meta-data android:name="xposeddescription" android:resource="@ref/0x7f020001"/><meta-data android:name="xposedscope" android:resource="@ref/0x7f030001"/></application></manifest>`,
    resources: `resource 0x7f010001 mipmap/ic_launcher\n (anydpi) (file) res/icon.xml type=XML\nresource 0x7f010002 mipmap/ic_launcher_foreground\n (xxxhdpi) (file) res/foreground.png type=PNG\nresource 0x7f010003 mipmap/ic_launcher_round\n (anydpi) (file) res/icon.xml type=XML\nresource 0x7f020001 string/xposed_description\n () "${description}"\nresource 0x7f030001 array/xposed_scope\n () (array) size=1\n ["android"]\n`,
    entries: {
      "META-INF/xposed/java_init.list": `${PREFIX}ModernEntry\n`,
      "assets/xposed_init": `${PREFIX}LegacyEntry\n`,
      "META-INF/xposed/module.prop": "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\nautoHotReload=false\n",
      "META-INF/xposed/scope.list": "system\n",
    },
    files: ["res/icon.xml", "res/foreground.png"],
    iconXml: {
      "res/icon.xml":
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><foreground android:drawable="@ref/0x7f010002"/><background android:drawable="@ref/0x7f040001"/></adaptive-icon>',
    },
    brandedBitmaps: { "res/foreground.png": true },
    nativeLibs: { "lib/arm64-v8a/libface.so": true, "lib/arm64-v8a/libutil.so": true },
    dex: `C d 1 1 1 ${PREFIX}ModernEntry\nM d 1 1 1 ${PREFIX}ModernEntry <init>()\nC d 1 1 1 ${PREFIX}LegacyEntry\nM d 1 1 1 ${PREFIX}LegacyEntry <init>()\n${["BeforeHooker", "AfterHooker"].map((name) => `C d 1 1 1 ${PREFIX}ModernHookFacade$${name}\nM d 1 1 1 ${PREFIX}ModernHookFacade$${name} java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain)\n`).join("")}`,
  };
}
const expected = { versionName: "1.1.0", versionCode: "2", description, abis: ["arm64-v8a"] };

test("现代框架调用的前后置拦截器必须保留完整回调签名", () => {
  for (const hooker of ["BeforeHooker", "AfterHooker"]) {
    const name = `${PREFIX}ModernHookFacade$${hooker}`;
    for (const replacement of [
      "",
      "java.lang.Object intercept(java.lang.Object)",
      "void intercept(io.github.libxposed.api.XposedInterface$Chain)",
    ]) {
      const data = fixture();
      data.dex = data.dex.replace(
        `${name} java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain)`,
        `${name} ${replacement}`,
      );
      assert.match(validateApk(data, expected).join("\n"), /拦截器.*intercept/);
    }
    const data = fixture();
    data.dex = data.dex.replace(`C d 1 1 1 ${name}\n`, "");
    assert.match(validateApk(data, expected).join("\n"), /拦截器类/);
  }
});

test("调试故障注入只允许出现在可调试包", () => {
  const injection = "cn.rainafter.app.gazekeep.backend.shizuku.UserActivityFaultInjection";
  const release = fixture();
  release.dex += `C d 1 1 1 ${injection}\n`;
  assert.match(validateApk(release, expected).join("\n"), /不可调试包含调试故障注入/);
  const debug = fixture();
  debug.dex = release.dex;
  debug.manifest = debug.manifest.replace("<application", '<application android:debuggable="true"');
  assert.deepEqual(validateApk(debug, expected), []);
});

test("完整双入口、中文简介与品牌资源通过", () => {
  assert.deepEqual(validateApk(fixture(), expected), []);
});

test("缺失或错误的入口、API、作用域、热重载声明被拒绝", () => {
  for (const [key, value, match] of [
    ["META-INF/xposed/java_init.list", undefined, /现代入口/],
    ["assets/xposed_init", `${PREFIX}WrongEntry`, /旧入口/],
    ["META-INF/xposed/module.prop", "minApiVersion=93\ntargetApiVersion=100", /minApiVersion|targetApiVersion/],
    [
      "META-INF/xposed/module.prop",
      "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\nautoHotReload=true",
      /autoHotReload/,
    ],
    ["META-INF/xposed/scope.list", "android\nsystem", /现代作用域/],
  ] as [string, string | undefined, RegExp][]) {
    const data = fixture();
    data.entries[key] = value;
    assert.match(validateApk(data, expected).join("\n"), match);
  }
});

test("编译权限、资源、版本和无参反射入口的缺陷被拒绝", () => {
  for (const [mutate, match] of [
    [
      (s) => {
        s.manifest = s.manifest.replace(
          "<application",
          '<uses-permission android:name="android.permission.INTERNET"/><application',
        );
      },
      /禁止权限/,
    ],
    [
      (s) => {
        s.manifest = s.manifest.replace('versionCode="2"', 'versionCode="1"');
      },
      /版本/,
    ],
    [
      (s) => {
        s.manifest = s.manifest.replace('android:description="@ref/0x7f020001"', "");
      },
      /简介引用/,
    ],
    [
      (s) => {
        s.resources = s.resources.replace(description, "old description");
      },
      /中文简介/,
    ],
    [
      (s) => {
        s.dex = s.dex.replace(`${PREFIX}ModernEntry <init>()`, `${PREFIX}ModernEntry <init>(java.lang.Object)`);
      },
      /无参构造/,
    ],
    [
      (s) => {
        s.dex += "C d 1 1 1 io.github.libxposed.api.XposedModule\n";
      },
      /框架 API/,
    ],
    [
      (s) => {
        s.dex += "C d 1 1 1 android.app.ActivityThread\n";
      },
      /隐藏存根/,
    ],
    [
      (s) => {
        s.files = [];
      },
      /图标文件/,
    ],
    [
      (s) => {
        s.brandedBitmaps["res/foreground.png"] = false;
      },
      /品牌图标/,
    ],
    [
      (s) => {
        s.iconXml["res/icon.xml"] =
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><foreground android:drawable="@ref/0x7f010099"/></adaptive-icon>';
      },
      /自适应前景/,
    ],
  ] as [(s: ApkSnapshot) => void, RegExp][]) {
    const data = fixture();
    mutate(data);
    assert.match(validateApk(data, expected).join("\n"), match);
  }
});

test("DEX 对框架类型的正常引用不被误判为定义", () => {
  const data = fixture();
  data.dex += `M d 1 1 1 ${PREFIX}ModernEntry void callback(io.github.libxposed.api.XposedModule)\n`;
  assert.deepEqual(validateApk(data, expected), []);
});

test("Manifest 反射登记的 ML Kit 注册器必须保留无参构造器", () => {
  const name = "com.google.mlkit.vision.face.internal.FaceRegistrar";
  const data = fixture();
  data.manifest = data.manifest.replace(
    "</application>",
    `<service android:name="com.google.mlkit.common.internal.MlKitComponentDiscoveryService"><meta-data android:name="com.google.firebase.components:${name}" android:value="com.google.firebase.components.ComponentRegistrar"/></service></application>`,
  );
  data.dex += `C d 1 1 1 ${name}\n`;
  assert.match(validateApk(data, expected).join("\n"), /注册器.*无参构造/);
  data.dex += `M d 1 1 1 ${name} <init>()\n`;
  assert.deepEqual(validateApk(data, expected), []);
  data.dex = data.dex.replace(`C d 1 1 1 ${name}\n`, "");
  assert.match(validateApk(data, expected).join("\n"), /注册器.*类/);
});

test("原生库 ABI 必须与预期拆分一致且压缩存放", () => {
  const universal = { ...expected, abis: ["arm64-v8a", "armeabi-v7a"] };
  const data = fixture();
  data.nativeLibs["lib/armeabi-v7a/libface.so"] = true;
  assert.deepEqual(validateApk(data, universal), []);
  assert.match(validateApk(data, expected).join("\n"), /多余 ABI.*armeabi-v7a/);
  assert.match(validateApk(fixture(), universal).join("\n"), /缺少 ABI.*armeabi-v7a/);
  const stored = fixture();
  stored.nativeLibs["lib/arm64-v8a/libface.so"] = false;
  assert.match(validateApk(stored, expected).join("\n"), /未压缩.*libface\.so/);
  const empty = fixture();
  empty.nativeLibs = {};
  assert.match(validateApk(empty, expected).join("\n"), /缺少 ABI.*arm64-v8a/);
});

// 只构造中央目录与目录尾记录，足以覆盖按中央目录读取压缩方式的逻辑。
function centralDirectoryZip(entries: [string, number][]) {
  const records = entries.map(([name, method]) => {
    const bytes = Buffer.from(name, "utf8");
    const record = Buffer.alloc(46 + bytes.length);
    record.writeUInt32LE(0x02014b50, 0);
    record.writeUInt16LE(method, 10);
    record.writeUInt16LE(bytes.length, 28);
    bytes.copy(record, 46);
    return record;
  });
  const directory = Buffer.concat(records);
  const prefix = Buffer.alloc(16, 0xaa);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(prefix.length, 16);
  return Buffer.concat([prefix, directory, end]);
}

test("从 ZIP 中央目录读取各条目的压缩方式", () => {
  const zip = centralDirectoryZip([
    ["lib/arm64-v8a/libface.so", 8],
    ["lib/arm64-v8a/libutil.so", 0],
    ["classes.dex", 8],
  ]);
  assert.deepEqual(Object.fromEntries(zipCompression(zip)), {
    "lib/arm64-v8a/libface.so": true,
    "lib/arm64-v8a/libutil.so": false,
    "classes.dex": true,
  });
  assert.throws(() => zipCompression(Buffer.alloc(30)), /ZIP 目录尾/);
});
