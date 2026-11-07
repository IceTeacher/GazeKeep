/** 同站代理 Umami：脚本与上报走 /stats/*，避免第三方域名被拦截，也不必为统计服务配置跨域。 */
const UPSTREAM = "https://umami.rainafter.cn";
const PREFIX = "/stats/";
// 只转发 Umami 需要的请求头；官网 Cookie 等不发往统计服务。
const FORWARDED_HEADERS = ["accept", "accept-language", "content-type", "user-agent", "referer"];

/** EdgeOne 边缘函数在 request.eo 上提供访客信息；只声明用到的字段，避免引入平台类型依赖。 */
type EdgeRequest = Request & { eo?: { clientIp?: string } };

/** EdgeOne 边缘函数入口；`[[path]]` 匹配 /stats/ 下任意层级。 */
export async function onRequest({ request }: { request: EdgeRequest }) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith(PREFIX)) return new Response("Not Found", { status: 404 });
  const headers = new Headers();
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  // 经边缘节点转发后上游只能看到 EdgeOne 出口地址，以平台识别的访客 IP 供 Umami 判定地区；
  // 不透传客户端自带的 X-Forwarded-For，避免伪造。
  const client = request.eo?.clientIp;
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
