import test from "node:test";
import assert from "node:assert/strict";
import { createShowcaseState, createShowcaseAttempts, chooseRuntime } from "../src/scripts/showcase-state.mjs";
import { assertPrivacyInPolicy, extractPrivacy } from "../scripts/privacy.mjs";

test("桥接延迟就绪时重放最新页面、状态和主题", () => {
  const state = createShowcaseState();
  state.update({ route: "backend", status: "backend_shizuku", locale: "en", dark: false });
  const calls = [];
  state.attach({
    setScreen: (x) => calls.push(x),
    setDemoStatus: (x) => calls.push(x),
    setLocaleAndTheme: (...x) => calls.push(x),
  });
  assert.deepEqual(calls, [["en", false], "backend", "backend_shizuku"]);
  state.update({ route: "settings", status: "dim_fallback" });
  assert.deepEqual(calls.slice(-3), [["en", false], "settings", "dim_fallback"]);
});
test("失败后可重试，错误状态不覆盖最新展示目标", () => {
  const state = createShowcaseState();
  state.fail();
  state.update({ route: "whitelist" });
  assert.equal(state.snapshot().phase, "error");
  state.loading();
  assert.equal(state.snapshot().route, "whitelist");
  assert.equal(state.snapshot().phase, "loading");
});
test("Wasm 能力不足时选择 JS", () => {
  assert.equal(chooseRuntime(false), "js");
  assert.equal(chooseRuntime(true), "wasm");
});
test("加载事件只作用于当前尝试，重复错误只触发一次降级", () => {
  const attempts = createShowcaseAttempts();
  const wasm = attempts.begin();
  assert.equal(attempts.fail(wasm), true);
  assert.equal(attempts.fail(wasm), false);
  const js = attempts.begin();
  assert.equal(attempts.ready(wasm), false);
  assert.equal(attempts.fail(wasm), false);
  assert.equal(attempts.ready(js), true);
  assert.equal(attempts.fail(js), true);
  assert.equal(attempts.fail(js), false);
});
test("隐私资源解析 XML 实体并拒绝缺失或重复", () => {
  assert.deepEqual(
    extractPrivacy(
      '<resources><string name="privacy_line_1">A &amp; B</string><string name="privacy_line_2">C</string><string name="privacy_line_3">D</string></resources>',
    ),
    ["A & B", "C", "D"],
  );
  assert.throws(() => extractPrivacy("<resources/>"), /privacy_line_1/);
  assert.throws(
    () => extractPrivacy('<string name="privacy_line_1">A</string><string name="privacy_line_1">B</string>'),
    /privacy_line_1/,
  );
});
test("隐私文档同时约束中英文资源", () => {
  const policy = "中文一\n中文二\n中文三\nEnglish one\nEnglish two\nEnglish three";
  assert.doesNotThrow(() =>
    assertPrivacyInPolicy(policy, ["中文一", "中文二", "中文三"], ["English one", "English two", "English three"]),
  );
  assert.throws(
    () => assertPrivacyInPolicy(policy, ["中文一", "中文二", "中文三"], ["English one", "Changed", "English three"]),
    /Changed/,
  );
});
