import { expect, test } from "vitest";
import { getShowcaseHomePreview, parseAndroidStrings, readAppVersionName } from "../../../src/data/showcase-preview.ts";

test("从 Android 词条表与构建配置提取展示主页预渲染文案与版本号", () => {
  const version = readAppVersionName();
  expect(version).toMatch(/^v\d+\.\d+\.\d+/);

  const zh = getShowcaseHomePreview("zh");
  expect(zh.title).toBe("注视保亮");
  expect(zh.working).toBe("工作中");
  expect(zh.lastDecision).toBe("最近检测：尚无检测结果");
  expect(zh.appVersionValue).toBe(version);
  expect(zh.backends).toHaveLength(3);

  const en = getShowcaseHomePreview("en");
  expect(en.title).toBe("GazeKeep");
  expect(en.working).toBe("Running");
  expect(en.lastDecision).toBe("Last check: No recent check");
  expect(en.appVersionValue).toBe(version);
  expect(en.navHome).toBe("Home");
  expect(en.navSettings).toBe("Settings");
});

test("parseAndroidStrings 支持实体反转义并保留空白修剪", () => {
  const parsed = parseAndroidStrings(`
    <resources>
      <string name="sample">  It\\'s &amp; Ready  </string>
    </resources>
  `);
  expect(parsed.sample).toBe("It's & Ready");
});
