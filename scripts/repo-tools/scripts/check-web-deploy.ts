import { setTimeout as sleep } from "node:timers/promises";
import { cli, context, isMain, report } from "./common.ts";

type Fetcher = (address: string | URL | Request, init?: RequestInit) => Promise<Response>;
const PROBES: { path: string; status: number; location?: string }[] = [
  { path: "/", status: 302, location: "/zh/" },
  { path: "/zh/", status: 200 },
  { path: "/en/", status: 200 },
  { path: "/kmp-showcase/wasm/", status: 200 },
  { path: "/kmp-showcase/js/", status: 200 },
];

async function probeOnce(base: URL, fetcher: Fetcher) {
  const errors: string[] = [];
  for (const probe of PROBES) {
    const url = new URL(probe.path, base);
    try {
      // 根路径跳转本身是验收对象，不能让 fetch 自动跟随。
      const response = await fetcher(url, { redirect: "manual", signal: AbortSignal.timeout(15_000) });
      if (response.status !== probe.status) errors.push(`${probe.path} 返回 ${response.status}，预期 ${probe.status}`);
      else if (probe.location) {
        const target = new URL(response.headers.get("location") ?? "", url);
        if (target.origin !== base.origin || target.pathname !== probe.location)
          errors.push(`${probe.path} 跳转到 ${target.pathname}，预期 ${probe.location}`);
      }
    } catch (error) {
      errors.push(`${probe.path} 请求失败：${error instanceof Error ? error.message : String(error)}`);
    }
  }
  return errors;
}

// 新部署在边缘节点生效前可能短暂失败；只在全部探测通过时提前结束。
export async function checkWebDeploy(
  address: string,
  { fetcher = fetch, attempts = 6, delayMs = 10_000 }: { fetcher?: Fetcher; attempts?: number; delayMs?: number } = {},
) {
  const base = new URL(address);
  if (base.protocol !== "https:") throw new Error(`部署地址必须使用 https：${address}`);
  if (base.pathname !== "/" || base.search || base.hash) throw new Error(`部署地址必须是站点根：${address}`);
  let errors: string[] = [];
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    errors = await probeOnce(base, fetcher);
    if (!errors.length) return [];
    if (attempt < attempts) await sleep(delayMs);
  }
  return errors;
}

if (isMain(import.meta.url))
  await cli(async () => {
    const { values } = context({ url: { type: "string" } });
    if (!values.url) throw new Error("用法：check-web-deploy.ts --url <站点根地址>");
    report(await checkWebDeploy(values.url), `官网部署冒烟通过：${values.url}`);
  });
