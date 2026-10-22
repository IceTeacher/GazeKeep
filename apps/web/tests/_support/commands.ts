import type { BrowserCommand } from "vitest/node";

type Media = { reducedMotion?: "reduce" | "no-preference" | null; colorScheme?: "dark" | "light" | null };

/** 由 Playwright 模拟系统偏好；媒体查询的 change 事件与真实切换一致。 */
export const emulateMedia: BrowserCommand<[Media]> = async (context, media) => {
  if (context.provider.name !== "playwright") throw new Error("emulateMedia 只支持 Playwright provider");
  await context.page.emulateMedia(media);
};

declare module "vitest/browser" {
  interface BrowserCommands {
    emulateMedia: (media: Media) => Promise<void>;
  }
}
