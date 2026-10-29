import { expect, test } from "vitest";
import {
  chooseRuntime,
  createRenderGate,
  createShowcaseAttempts,
  createShowcaseState,
  createDriftTracker,
  liveStatusText,
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

test("访客操作后，页面滚动超过阈值才复位一次", () => {
  const drift = createDriftTracker(24);
  expect(drift.shouldRestore(500)).toBe(false);
  drift.mark(1000);
  expect(drift.active()).toBe(true);
  // 点击时的细微抖动或惯性余量不算滚动。
  expect(drift.shouldRestore(1010)).toBe(false);
  expect(drift.shouldRestore(990)).toBe(false);
  expect(drift.shouldRestore(1024)).toBe(true);
  expect(drift.active()).toBe(false);
  expect(drift.shouldRestore(2000)).toBe(false);
});

test("向上滚动同样复位，再次操作以新位置为起点", () => {
  const drift = createDriftTracker(24);
  drift.mark(1000);
  drift.mark(1020);
  expect(drift.shouldRestore(1000)).toBe(false);
  expect(drift.shouldRestore(996)).toBe(true);
  drift.mark(400);
  drift.clear();
  expect(drift.shouldRestore(0)).toBe(false);
});

const labels = {
  status: { idle: "准备就绪", sampling: "临近熄屏 · 短时采样" },
  hint: { touch: "触摸提示", mouse: "鼠标提示" },
};

test("首屏主页空闲且内屏可操作时，按主要输入方式显示操作提示", () => {
  const base = { route: "home", status: "idle", ready: true };
  expect(liveStatusText(labels, { ...base, finePointer: true })).toBe("鼠标提示");
  expect(liveStatusText(labels, { ...base, finePointer: false })).toBe("触摸提示");
});

test("内屏未就绪或处于其他阶段时保留演示状态文案", () => {
  expect(liveStatusText(labels, { route: "home", status: "idle", ready: false, finePointer: true })).toBe("准备就绪");
  expect(liveStatusText(labels, { route: "whitelist", status: "idle", ready: true, finePointer: true })).toBe(
    "准备就绪",
  );
  expect(liveStatusText(labels, { route: "home", status: "sampling", ready: true, finePointer: false })).toBe(
    "临近熄屏 · 短时采样",
  );
});
