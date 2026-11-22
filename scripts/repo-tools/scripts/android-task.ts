import { join } from "node:path";
import { ANDROID_FULL_VERIFY_TASKS, ANDROID_HOST_TEST_TASKS, CORE_WEB_TEST_TASKS } from "./android-modules.ts";
import { cli, context, run } from "./common.ts";

export const TEST_TASKS = ANDROID_HOST_TEST_TASKS;

await cli(() => {
  const { android, positionals } = context({}, true);
  const [command, ...rest] = positionals;
  if (command === "tasks") {
    if (!rest.length) throw new Error("用法：android-task.ts tasks <Gradle任务...>");
    for (const task of rest) {
      if (!/^:[a-zA-Z0-9:_-]+$/.test(task)) throw new Error(`非法 Gradle 任务名：${task}`);
    }
    const args = rest.includes("--console=plain") ? rest : [...rest, "--console=plain"];
    run(join(android, "gradlew"), args, { cwd: android });
    return;
  }
  const tasks: Record<string, readonly string[]> = {
    build: [":android:assembleDebug"],
    lint: [":android:lintDebug"],
    test: TEST_TASKS,
    "core-web-test": CORE_WEB_TEST_TASKS,
    // 综合验证编译实机套件，确保其随实现演进；实际运行需设备与授权，单独由 device 执行。
    verify: ANDROID_FULL_VERIFY_TASKS,
    // 实机套件为独立进程的自插桩模块；保留安装，避免 AGP 在结束后卸载被测应用并清空用户数据。
    device: [":e2e:connectedDebugAndroidTest", "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"],
  };
  if (positionals.length !== 1 || !Object.hasOwn(tasks, command))
    throw new Error("用法：android-task.ts build|lint|test|core-web-test|verify|device|tasks <任务...>");
  // 综合验证只启动一个 Gradle 进程，模块依赖继续由 Gradle 管理。
  run(join(android, "gradlew"), [...tasks[command]], { cwd: android });
});
