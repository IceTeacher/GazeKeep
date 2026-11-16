import { readFileSync } from "node:fs";
import { expect, test } from "vitest";
import { assertEdgeoneRoutes, edgeoneChildEnv, findLeakedEnv } from "../../scripts/edgeone.ts";

// 摘自 edgeone 1.6.47 `makers generate-routes` 对本站配置的实际输出。
const generated = {
  version: 3,
  routes: [{ handle: "filesystem" }, { src: "^/stats/(.*)$", "server-name": "edge" }],
  conf: { headers: [], redirects: [{ source: "/", destination: "/zh/", statusCode: 302 }], caches: [] },
};

test("打包子进程只拿到运行 CLI 所需的环境变量，部署凭据不会进入构建", () => {
  const env = edgeoneChildEnv({
    PATH: "/usr/bin",
    HOME: "/home/runner",
    TMPDIR: "/tmp/x",
    EDGEONE_API_TOKEN: "token-should-not-leak",
    GITHUB_TOKEN: "ghs_should_not_leak",
  });
  expect(env).toEqual({ PATH: "/usr/bin", HOME: "/home/runner", TMPDIR: "/tmp/x", CI: "true" });
  expect(edgeoneChildEnv({})).toEqual({ PATH: "", HOME: "", CI: "true" });
});

test("边缘函数包含有父进程环境变量的值时报告变量名，不报告值本身", () => {
  const parent = { PATH: "/usr/bin:/bin", SECRET_KEY: "sk-test-1234567890", SHORT: "true", EMPTY: "" };
  const allowed = edgeoneChildEnv(parent);
  const bundle = `const env = {"PATH":"/usr/bin:/bin","CI":"true","LEAK":"sk-test-1234567890"};`;
  expect(findLeakedEnv(bundle, parent, allowed)).toEqual(["SECRET_KEY"]);
  expect(findLeakedEnv(`const env = {"PATH":"/usr/bin:/bin","CI":"true"};`, parent, allowed)).toEqual([]);
});

test("上传目录的路由必须保留根路径 302 与统计代理边缘函数", () => {
  expect(() => assertEdgeoneRoutes(generated)).not.toThrow();
  expect(() => assertEdgeoneRoutes({ ...generated, conf: { redirects: [] } })).toThrow(/302/);
  const permanent = { ...generated, conf: { redirects: [{ source: "/", destination: "/zh/", statusCode: 301 }] } };
  expect(() => assertEdgeoneRoutes(permanent)).toThrow(/302/);
  expect(() => assertEdgeoneRoutes({ ...generated, routes: [{ handle: "filesystem" }] })).toThrow(/\/stats/);
  expect(() => assertEdgeoneRoutes(null)).toThrow(/routes\.json/);
});

test("edgeone.json 为哈希 .wasm、入口脚本与 /_astro 静态分块配置 immutable 强缓存并保留 devCommand", () => {
  const cfg = JSON.parse(readFileSync(new URL("../../edgeone.json", import.meta.url), "utf8")) as {
    devCommand?: string;
    headers?: { source: string; headers: { key: string; value: string }[] }[];
  };
  expect(cfg.devCommand).toBe("astro dev --host");
  const cacheFor = (source: string) =>
    cfg.headers?.find((item) => item.source === source)?.headers.find((h) => h.key === "Cache-Control")?.value;
  expect(cacheFor("/_astro/*")).toBe("public, max-age=31536000, immutable");
  expect(cacheFor("/kmp-showcase/wasm/*.wasm")).toBe("public, max-age=31536000, immutable");
  expect(cacheFor("/kmp-showcase/js/*.wasm")).toBe("public, max-age=31536000, immutable");
  expect(cacheFor("/kmp-showcase/wasm/shared.*.js")).toBe("public, max-age=31536000, immutable");
});
