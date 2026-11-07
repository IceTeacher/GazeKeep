import { expect, test } from "vitest";
import { readFile } from "node:fs/promises";

test("根路径由 EdgeOne 服务端 302 跳转到中文站", async () => {
  const config = JSON.parse(await readFile(new URL("../../edgeone.json", import.meta.url), "utf8")) as {
    redirects?: { source: string; destination: string; statusCode?: number }[];
  };
  expect(config.redirects?.find(({ source }) => source === "/")).toEqual({
    source: "/",
    destination: "/zh/",
    statusCode: 302,
  });
});
