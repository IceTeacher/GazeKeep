import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { fileURLToPath } from "node:url";
import type { TestProject } from "vitest/node";

export type RenderedPage = { lang: string; body: string };

declare module "vitest" {
  export interface ProvidedContext {
    pages: Record<"zh" | "en", RenderedPage>;
  }
}

const root = fileURLToPath(new URL("../../", import.meta.url));

function freePort() {
  return new Promise<number>((resolve, reject) => {
    const probe = createServer().listen(0, "127.0.0.1", () => {
      const { port } = probe.address() as { port: number };
      probe.close(() => resolve(port));
    });
    probe.on("error", reject);
  });
}

// 继承 Vitest 注入的测试环境变量时，子进程中的 Astro 开发服务无法就绪。
const devEnv = () =>
  Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^(VITEST|NODE_ENV$|TEST$)/.test(key)));

/** 由独立的 Astro 开发服务渲染真实页面标记；浏览器侧只注入 body，脚本由测试显式初始化。
 * Vitest 的 globalSetup 运行器会内联 Astro，Container 与编程式 dev 在其中都无法以服务端环境运行，因此使用子进程。 */
export default async function setup(project: TestProject) {
  const port = await freePort();
  const astro = spawn(
    process.execPath,
    [`${root}node_modules/astro/bin/astro.mjs`, "dev", "--ignore-lock", "--host", "127.0.0.1", "--port", String(port)],
    {
      cwd: root,
      env: { ...devEnv(), ASTRO_TELEMETRY_DISABLED: "1" },
      stdio: ["ignore", "ignore", "pipe"],
      detached: true,
    },
  );
  let stderr = "";
  astro.stderr.on("data", (chunk) => (stderr += chunk));
  const fetchPage = async (lang: string) => {
    for (let i = 0; i < 300; i++) {
      if (astro.exitCode !== null) throw new Error(`Astro 开发服务退出：${stderr}`);
      try {
        const response = await fetch(`http://127.0.0.1:${port}/${lang}/`);
        if (response.ok) return await response.text();
      } catch {
        /* 服务尚未就绪。 */
      }
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    throw new Error(`Astro 开发服务 30 秒内未就绪：${stderr}`);
  };
  try {
    const render = async (lang: "zh" | "en"): Promise<RenderedPage> => {
      const body = /<body[^>]*>([\s\S]*)<\/body>/.exec(await fetchPage(lang))?.[1];
      if (!body) throw new Error(`/${lang}/ 渲染结果缺少 body`);
      // 去掉页面模块脚本与开发工具栏，保留 JSON 数据脚本。
      return {
        lang,
        body: body
          .replace(/<script(?![^>]*application\/json)[\s\S]*?<\/script>/g, "")
          .replace(/<astro-dev-toolbar[\s\S]*?<\/astro-dev-toolbar>/g, ""),
      };
    };
    project.provide("pages", { zh: await render("zh"), en: await render("en") });
  } finally {
    // 结束整个进程组，避免开发服务的子进程残留。
    if (astro.pid) process.kill(-astro.pid, "SIGTERM");
  }
}
