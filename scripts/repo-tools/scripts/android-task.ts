import { join } from "node:path";
import { cli, context, run } from "./common.ts";

export const TEST_TASKS = [
  ":core:allTests",
  ":engine:testDebugUnitTest",
  ":backend:a11y:testDebugUnitTest",
  ":backend:shizuku:testDebugUnitTest",
  ":xposed:testDebugUnitTest",
  ":android:testDebugUnitTest",
];
await cli(() => {
  const { android, positionals } = context({}, true);
  const tasks: Record<string, string[]> = {
    build: [":android:assembleDebug"],
    lint: [":android:lintDebug"],
    test: TEST_TASKS,
    verify: [":android:assembleDebug", ":android:lintDebug", ...TEST_TASKS],
  };
  if (positionals.length !== 1 || !Object.hasOwn(tasks, positionals[0]))
    throw new Error("用法：android-task.ts build|lint|test|verify");
  // 综合验证只启动一个 Gradle 进程，模块依赖继续由 Gradle 管理。
  run(join(android, "gradlew"), tasks[positionals[0]], { cwd: android });
});
