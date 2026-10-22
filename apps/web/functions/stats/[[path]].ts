/** 同站代理 Umami：脚本与上报走 /stats/*，避免第三方域名被拦截，也不必为统计服务配置跨域。 */
const UPSTREAM = "https://umami.rainafter.cn";
const PREFIX = "/stats/";
// 只转发 Umami 需要的请求头；官网 Cookie 等不发往统计服务。
const FORWARDED_HEADERS = ["accept", "accept-language", "content-type", "user-agent", "referer"];

/** Cloudflare Pages Functions 入口；只用到 request，避免引入 workers-types 依赖。 */
export async function onRequest({ request }: { request: Request }) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith(PREFIX)) return new Response("Not Found", { status: 404 });
  const headers = new Headers();
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  // 经 Worker 转发后上游只能看到 Cloudflare 出口地址，以访客 IP 供 Umami 判定地区。
  const client = request.headers.get("cf-connecting-ip");
  if (client) headers.set("x-forwarded-for", client);
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  return fetch(
    new Request(`${UPSTREAM}/${url.pathname.slice(PREFIX.length)}${url.search}`, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      redirect: "manual",
    }),
  );
}
