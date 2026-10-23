import { afterEach, expect, test, vi } from "vitest";
import { onRequest } from "../../../functions/stats/[[path]].ts";

afterEach(() => vi.unstubAllGlobals());

/** 记录转发出去的请求，返回上游响应。 */
function stubUpstream(response = new Response("ok", { status: 202, headers: { "content-type": "text/plain" } })) {
  const calls: Request[] = [];
  vi.stubGlobal("fetch", async (input: Request) => {
    calls.push(input);
    return response;
  });
  return calls;
}

test("脚本请求保留子路径与查询参数转发到 Umami，并原样返回上游响应", async () => {
  const calls = stubUpstream();
  const response = await onRequest({
    request: new Request("https://gazekeep.rainafter.cn/stats/api/websites/abc/recorder?x=1"),
  });
  expect(calls.map((request) => request.url)).toEqual(["https://umami.rainafter.cn/api/websites/abc/recorder?x=1"]);
  expect(response.status).toBe(202);
  expect(await response.text()).toBe("ok");
});

const send = (body: string, ip?: string) =>
  onRequest({
    request: new Request("https://gazekeep.rainafter.cn/stats/api/send", {
      method: "POST",
      body,
      headers: {
        "content-type": "application/json",
        "user-agent": "Mozilla/5.0 Test",
        cookie: "site=secret",
        ...(ip ? { "cf-connecting-ip": ip } : {}),
      },
    }),
  });

test("上报以访客 IP 写入 payload.ip，保留方法、其余字段与 User-Agent", async () => {
  const calls = stubUpstream();
  // 客户端自带的 ip 以 Cloudflare 识别的访客地址为准。
  await send('{"type":"event","payload":{"website":"w","url":"/zh/","ip":"198.51.100.1"}}', "203.0.113.7");
  const [forwarded] = calls;
  expect(forwarded.method).toBe("POST");
  expect(await forwarded.json()).toEqual({ type: "event", payload: { website: "w", url: "/zh/", ip: "203.0.113.7" } });
  expect(forwarded.headers.get("user-agent")).toBe("Mozilla/5.0 Test");
  // Vercel 会覆盖 X-Forwarded-For，不再写入；官网 Cookie 不发往统计服务。
  expect(forwarded.headers.get("x-forwarded-for")).toBeNull();
  expect(forwarded.headers.get("cookie")).toBeNull();
});

test("缺少访客 IP 或请求体不是上报 JSON 时原样转发", async () => {
  const calls = stubUpstream();
  await send('{"type":"event","payload":{"website":"w"}}');
  await send("not json", "203.0.113.7");
  await send('{"type":"event"}', "203.0.113.7");
  expect(await Promise.all(calls.map((request) => request.text()))).toEqual([
    '{"type":"event","payload":{"website":"w"}}',
    "not json",
    '{"type":"event"}',
  ]);
});

test("会话录制等其他接口不改写请求体", async () => {
  const calls = stubUpstream();
  const body = '{"website":"w","events":[]}';
  await onRequest({
    request: new Request("https://gazekeep.rainafter.cn/stats/api/record", {
      method: "POST",
      body,
      headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.7" },
    }),
  });
  expect(await calls[0].text()).toBe(body);
});

test("非 /stats 路径不转发", async () => {
  const calls = stubUpstream();
  const response = await onRequest({ request: new Request("https://gazekeep.rainafter.cn/statsx/script.js") });
  expect(response.status).toBe(404);
  expect(calls).toEqual([]);
});
