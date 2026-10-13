import assert from "node:assert/strict";
import test from "node:test";
import { validateApk } from "../scripts/check-apk.mjs";

const PREFIX = "cn.rainafter.app.gazekeep.xposed.entry.";
const description = "注视屏幕时延后熄屏；看不清或离开时按系统超时熄屏。";
function fixture() {
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
    dex: `C d 1 1 1 ${PREFIX}ModernEntry\nM d 1 1 1 ${PREFIX}ModernEntry <init>()\nC d 1 1 1 ${PREFIX}LegacyEntry\nM d 1 1 1 ${PREFIX}LegacyEntry <init>()\n`,
  };
}
const expected = { versionName: "1.1.0", versionCode: "2", description };

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
  ]) {
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
  ]) {
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
