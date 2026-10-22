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

test("上报请求保留方法、请求体与 User-Agent，并以访客 IP 写入 X-Forwarded-For", async () => {
  const calls = stubUpstream();
  await onRequest({
    request: new Request("https://gazekeep.rainafter.cn/stats/api/send", {
      method: "POST",
      body: '{"type":"event"}',
      headers: {
        "content-type": "application/json",
        "user-agent": "Mozilla/5.0 Test",
        "cf-connecting-ip": "203.0.113.7",
        cookie: "site=secret",
      },
    }),
  });
  const [forwarded] = calls;
  expect(forwarded.method).toBe("POST");
  expect(await forwarded.text()).toBe('{"type":"event"}');
  expect(forwarded.headers.get("user-agent")).toBe("Mozilla/5.0 Test");
  expect(forwarded.headers.get("x-forwarded-for")).toBe("203.0.113.7");
  // 官网 Cookie 不发往统计服务。
  expect(forwarded.headers.get("cookie")).toBeNull();
});

test("非 /stats 路径不转发", async () => {
  const calls = stubUpstream();
  const response = await onRequest({ request: new Request("https://gazekeep.rainafter.cn/statsx/script.js") });
  expect(response.status).toBe(404);
  expect(calls).toEqual([]);
});
