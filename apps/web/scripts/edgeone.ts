/** EdgeOne 上传目录的打包约束：只传入最小环境，并在上传前核对路由与环境泄漏。 */

/**
 * `edgeone makers build` 未收到 ENV_STR 时把整个进程环境写入边缘函数包（edgeone 1.6.47 实测）。
 * 构建固定传入空的 ENV_STR，包内只剩 CLI 自带的少量开关；子进程环境再收窄为定位工具链所需变量，
 * 即使 CLI 行为变化，部署令牌等凭据也不会进入构建。
 */
export const EDGEONE_BUILD_ARGS = ["makers", "build", "--ENV_STR={}"];

export function edgeoneChildEnv(parent: NodeJS.ProcessEnv): Record<string, string> {
  const env: Record<string, string> = { PATH: parent.PATH ?? "", HOME: parent.HOME ?? "" };
  if (parent.TMPDIR) env.TMPDIR = parent.TMPDIR;
  env.CI = "true";
  return env;
}

/** 返回值出现在包内的父进程环境变量名；短值与有意传入的值不计，结果只含变量名。 */
export function findLeakedEnv(bundle: string, parent: NodeJS.ProcessEnv, allowed: Record<string, string>) {
  const passed = new Set(Object.values(allowed));
  return Object.entries(parent)
    .filter(([, value]) => value && value.length >= 8 && !passed.has(value) && bundle.includes(value))
    .map(([name]) => name);
}

type RouteFile = {
  routes?: { src?: string; "server-name"?: string }[];
  conf?: { redirects?: { source?: string; destination?: string; statusCode?: number }[] };
};

/** generate-routes 的输出必须保留根路径 302 与统计代理，否则上线后跳转或统计会静默失效。 */
export function assertEdgeoneRoutes(value: unknown) {
  if (!value || typeof value !== "object") throw new Error("routes.json 缺失或格式无效");
  const { routes = [], conf = {} } = value as RouteFile;
  const root = conf.redirects?.find((rule) => rule.source === "/");
  if (root?.destination !== "/zh/" || root.statusCode !== 302)
    throw new Error(`routes.json 缺少根路径到 /zh/ 的 302 跳转：${JSON.stringify(root ?? null)}`);
  if (!routes.some((route) => route.src?.startsWith("^/stats/") && route["server-name"] === "edge"))
    throw new Error("routes.json 缺少 /stats/ 边缘函数路由");
}
