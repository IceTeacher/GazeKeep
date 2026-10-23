/** 同站代理 Umami：脚本与上报走 /stats/*，避免第三方域名被拦截，也不必为统计服务配置跨域。 */
const UPSTREAM = "https://umami.rainafter.cn";
const PREFIX = "/stats/";
// 只转发 Umami 需要的请求头；官网 Cookie 等不发往统计服务。
const FORWARDED_HEADERS = ["accept", "accept-language", "content-type", "user-agent", "referer"];

/** Umami 部署在 Vercel，X-Forwarded-For 会被改写为 Worker 出口地址，地区请求头也按出口判定。
 * 上报的 payload.ip 存在时 Umami 跳过地区请求头、按该 IP 查 GeoLite，因此写入访客 IP；其余请求体原样转发。 */
async function forwardedBody(request: Request, path: string) {
  const body = await request.arrayBuffer();
  const client = request.headers.get("cf-connecting-ip");
  if (path !== "api/send" || !client) return body;
  try {
    const data = JSON.parse(new TextDecoder().decode(body));
    if (typeof data?.payload !== "object" || data.payload === null) return body;
    data.payload.ip = client;
    return JSON.stringify(data);
  } catch {
    return body;
  }
}

/** Cloudflare Pages Functions 入口；只用到 request，避免引入 workers-types 依赖。 */
export async function onRequest({ request }: { request: Request }) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith(PREFIX)) return new Response("Not Found", { status: 404 });
  const headers = new Headers();
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const path = url.pathname.slice(PREFIX.length);
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  return fetch(
    new Request(`${UPSTREAM}/${path}${url.search}`, {
      method: request.method,
      headers,
      body: hasBody ? await forwardedBody(request, path) : undefined,
      redirect: "manual",
    }),
  );
}
