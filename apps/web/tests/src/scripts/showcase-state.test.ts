import { expect, test } from "vitest";
import {
  chooseRuntime,
  createRenderGate,
  createShowcaseAttempts,
  createShowcaseState,
} from "../../../src/scripts/showcase-state.ts";

test("桥接延迟就绪时重放最新页面、状态和主题", () => {
  const state = createShowcaseState();
  state.update({ route: "backend", status: "backend_shizuku", locale: "en", dark: false });
  const calls: unknown[] = [];
  state.attach({
    setScreen: (x) => void calls.push(x),
    setDemoStatus: (x) => void calls.push(x),
    setLocaleAndTheme: (...x) => void calls.push(x),
  });
  expect(calls).toEqual([["en", false], "backend", "backend_shizuku"]);
  state.update({ route: "settings", status: "dim_fallback" });
  expect(calls.slice(-3)).toEqual([["en", false], "settings", "dim_fallback"]);
});

test("失败后可重试，错误状态不覆盖最新展示目标", () => {
  const state = createShowcaseState();
  state.fail();
  state.update({ route: "whitelist" });
  expect(state.snapshot().phase).toBe("error");
  state.loading();
  expect(state.snapshot().route).toBe("whitelist");
  expect(state.snapshot().phase).toBe("loading");
});

test("Wasm 能力不足时选择 JS", () => {
  expect(chooseRuntime(false)).toBe("js");
  expect(chooseRuntime(true)).toBe("wasm");
});

test("加载事件只作用于当前尝试，重复错误只触发一次降级", () => {
  const attempts = createShowcaseAttempts();
  const wasm = attempts.begin();
  expect(attempts.fail(wasm)).toBe(true);
  expect(attempts.fail(wasm)).toBe(false);
  const js = attempts.begin();
  expect(attempts.ready(wasm)).toBe(false);
  expect(attempts.fail(wasm)).toBe(false);
  expect(attempts.ready(js)).toBe(true);
  expect(attempts.fail(js)).toBe(true);
  expect(attempts.fail(js)).toBe(false);
});

test("绘制回执忽略过期编号和错误路由", () => {
  const g = createRenderGate();
  const a = g.request("privacy");
  const b = g.request("backend");
  expect(g.accept("privacy", a)).toBe(false);
  expect(g.accept("privacy", b)).toBe(false);
  expect(g.accept("backend", b)).toBe(true);
  expect(g.ready("backend")).toBe(true);
  g.reset();
  expect(g.ready("backend")).toBe(false);
});
