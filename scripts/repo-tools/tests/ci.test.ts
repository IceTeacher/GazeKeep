import assert from "node:assert/strict";
import test from "node:test";
import { classifyChanges } from "../scripts/ci-changes.ts";
import { checkWebDeploy } from "../scripts/check-web-deploy.ts";

test("改动范围按工程归类，官网随 shared 与 Gradle 根配置重建", () => {
  assert.deepEqual(classifyChanges(["docs/index.md", "scripts/repo-tools/scripts/check-docs.ts"]), {
    app: false,
    web: false,
    full: false,
  });
  assert.deepEqual(classifyChanges(["apps/app/engine/src/main/kotlin/A.kt"]), { app: true, web: false, full: false });
  assert.deepEqual(classifyChanges(["scripts/repo-tools/scripts/check-apk.ts"]), {
    app: true,
    web: false,
    full: false,
  });
  assert.deepEqual(classifyChanges(["apps/web/src/pages/zh/index.astro"]), { app: false, web: true, full: false });
  for (const path of [
    "apps/app/shared/src/commonMain/kotlin/A.kt",
    "apps/app/gradle/libs.versions.toml",
    "apps/app/kotlin-js-store/yarn.lock",
    "apps/app/settings.gradle.kts",
  ])
    assert.deepEqual(classifyChanges([path]), { app: true, web: true, full: false }, path);
});

test("根配置、锁文件、流水线或未知基线触发全量回归", () => {
  for (const path of [
    "package.json",
    "pnpm-lock.yaml",
    "turbo.json",
    ".oxlintrc.json",
    ".github/workflows/pipeline.yml",
  ])
    assert.deepEqual(classifyChanges([path]), { app: true, web: true, full: true }, path);
  assert.deepEqual(classifyChanges(null), { app: true, web: true, full: true });
  assert.deepEqual(classifyChanges(["apps/web/package.json"]), { app: false, web: true, full: false });
});

function responder(table: Record<string, [number, string?]>) {
  const calls: string[] = [];
  const fetcher = async (address: string | URL | Request, init?: RequestInit) => {
    assert.equal(init?.redirect, "manual");
    const url = new URL(String(address));
    calls.push(url.pathname);
    const [status, location] = table[url.pathname] ?? [404];
    return new Response(null, { status, headers: location ? { location } : {} });
  };
  return { calls, fetcher };
}
const healthy: Record<string, [number, string?]> = {
  "/": [302, "/zh/"],
  "/zh/": [200],
  "/en/": [200],
  "/kmp-showcase/wasm/": [200],
  "/kmp-showcase/js/": [200],
};

test("部署冒烟要求根路径 302 到中文站且双语与 KMP 入口可用", async () => {
  const { calls, fetcher } = responder(healthy);
  assert.deepEqual(await checkWebDeploy("https://dev.example.pages.dev", { fetcher, attempts: 1 }), []);
  assert.deepEqual(calls, Object.keys(healthy));
  const absolute = responder({ ...healthy, "/": [302, "https://dev.example.pages.dev/zh/"] });
  assert.deepEqual(
    await checkWebDeploy("https://dev.example.pages.dev/", { fetcher: absolute.fetcher, attempts: 1 }),
    [],
  );
});

test("跳转目标、状态码错误或地址非法时冒烟失败", async () => {
  const permanent = responder({ ...healthy, "/": [301, "/zh/"] });
  assert.match(
    (await checkWebDeploy("https://a.example", { fetcher: permanent.fetcher, attempts: 1 })).join("\n"),
    /\/ 返回 301/,
  );
  const english = responder({ ...healthy, "/": [302, "/en/"] });
  assert.match(
    (await checkWebDeploy("https://a.example", { fetcher: english.fetcher, attempts: 1 })).join("\n"),
    /跳转到 \/en\//,
  );
  const missing = responder({ ...healthy, "/kmp-showcase/js/": [404] });
  assert.match(
    (await checkWebDeploy("https://a.example", { fetcher: missing.fetcher, attempts: 1 })).join("\n"),
    /kmp-showcase\/js\/ 返回 404/,
  );
  await assert.rejects(checkWebDeploy("http://a.example", { fetcher: missing.fetcher, attempts: 1 }), /https/);
  await assert.rejects(checkWebDeploy("https://a.example/zh/", { fetcher: missing.fetcher, attempts: 1 }), /站点根/);
});

test("新部署传播期间按次数重试，网络错误计入失败", async () => {
  let round = 0;
  const flaky = async (address: string | URL | Request, init?: RequestInit) => {
    if (new URL(String(address)).pathname === "/") round += 1;
    if (round < 2) throw new Error("connection reset");
    return responder(healthy).fetcher(address, init);
  };
  assert.deepEqual(await checkWebDeploy("https://a.example", { fetcher: flaky, attempts: 3, delayMs: 0 }), []);
  assert.equal(round, 2);
  const down = async () => {
    throw new Error("connection reset");
  };
  assert.match(
    (await checkWebDeploy("https://a.example", { fetcher: down, attempts: 2, delayMs: 0 })).join("\n"),
    /connection reset/,
  );
});
